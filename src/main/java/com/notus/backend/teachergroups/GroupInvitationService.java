package com.notus.backend.teachergroups;

import com.notus.backend.auth.HashService;
import com.notus.backend.teachergroups.dto.InviteStudentRequest;
import com.notus.backend.teachergroups.dto.InviteStudentResponse;
import com.notus.backend.teachergroups.dto.GroupInvitationPreviewResponse;
import com.notus.backend.teachergroups.dto.GroupInvitationResponse;
import com.notus.backend.users.Role;
import com.notus.backend.users.Student;
import com.notus.backend.users.StudentRepository;
import com.notus.backend.users.Teacher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class GroupInvitationService {

    private static final Logger log = LoggerFactory.getLogger(GroupInvitationService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String GENERIC_INVITE_ERROR = "Nie udało się zaprosić ucznia. Skontaktuj się z administratorem.";
    private static final long RESEND_COOLDOWN_HOURS = 24;

    private final GroupInvitationRepository invitationRepository;
    private final TeacherGroupService groupService;
    private final HashService hashService;
    private final EmailService emailService;
    private final StudentRepository studentRepository;
    private final GroupMembershipRepository membershipRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String frontendBaseUrl;

    public GroupInvitationService(GroupInvitationRepository invitationRepository,
                                  TeacherGroupService groupService,
                                  HashService hashService,
                                  EmailService emailService,
                                  StudentRepository studentRepository,
                                  GroupMembershipRepository membershipRepository,
                                  @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.invitationRepository = invitationRepository;
        this.groupService = groupService;
        this.hashService = hashService;
        this.emailService = emailService;
        this.studentRepository = studentRepository;
        this.membershipRepository = membershipRepository;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public InviteStudentResponse invite(String teacherUid, Long groupId, InviteStudentRequest request) {
        try {
            String email = normalizeEmail(request.email());
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, GENERIC_INVITE_ERROR);
            }

            TeacherGroup group = groupService.requireOwnedGroup(teacherUid, groupId);
            InviteStudentResponse existingStudentCheck = checkExistingStudent(email, group);
            if (existingStudentCheck != null) {
                return existingStudentCheck;
            }

            List<GroupInvitation> emailInvitations = invitationsForEmail(group, email);
            if (hasActiveAcceptedInvitation(emailInvitations)) {
                return new InviteStudentResponse(false, "Ten uczeń jest już w grupie.");
            }
            Instant emailResendAvailableAt = latestResendAvailableAt(emailInvitations);
            GroupInvitation invitation = emailInvitations.stream()
                    .filter(item -> item.getStatus() != GroupInvitationStatus.ACCEPTED)
                    .findFirst()
                    .orElse(null);
            if (invitation != null && invitation.getStatus() == GroupInvitationStatus.PENDING
                    && invitation.getExpiresAt().isAfter(Instant.now())
                    && emailResendAvailableAt != null
                    && Instant.now().isBefore(emailResendAvailableAt)) {
                return new InviteStudentResponse(false,
                        "Zaproszenie na ten email zostało już wysłane. Ponów za " + cooldownLabel(emailResendAvailableAt) + ".");
            }
            if (invitation == null) {
                invitation = new GroupInvitation();
                invitation.setGroup(group);
                invitation.setEmail(email);
                invitation.setCreatedByTeacher(group.getTeacher());
            }

            sendInvitation(invitation, group);
            return new InviteStudentResponse(true, "Zaproszenie zostało wysłane.");
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().is4xxClientError() && ex.getStatusCode() != HttpStatus.FORBIDDEN) {
                log.warn("Group invitation rejected for group {}: {}", groupId, ex.getReason());
                return new InviteStudentResponse(false, GENERIC_INVITE_ERROR);
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Could not send group invitation for group {}", groupId, ex);
            return new InviteStudentResponse(false, GENERIC_INVITE_ERROR);
        }
    }

    private InviteStudentResponse checkExistingStudent(String email, TeacherGroup group) {
        Student student = studentRepository.findByEmailIgnoreCase(email).orElse(null);
        if (student == null) {
            return null;
        }

        if (student.getRole() != Role.STUDENT) {
            return new InviteStudentResponse(false, GENERIC_INVITE_ERROR);
        }

        if (membershipRepository.existsByGroupAndStudentAndStatus(group, student, GroupMembershipStatus.ACTIVE)) {
            return new InviteStudentResponse(false, "Ten uczeń jest już w grupie.");
        }

        return null;
    }

    @Transactional(readOnly = true)
    public GroupInvitationPreviewResponse preview(String rawToken) {
        try {
            GroupInvitation invitation = requirePendingByRawToken(rawToken);
            return new GroupInvitationPreviewResponse(
                    true,
                    invitation.getGroup().getId(),
                    invitation.getGroup().getName(),
                    invitation.getCreatedByTeacher().getName(),
                    invitation.getEmail(),
                    invitation.getExpiresAt(),
                    "Zaproszenie jest aktywne."
            );
        } catch (ResponseStatusException ex) {
            return new GroupInvitationPreviewResponse(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    ex.getReason() != null ? ex.getReason() : "Zaproszenie jest nieprawidłowe albo wygasło."
            );
        }
    }

    @Transactional
    public List<GroupInvitationResponse> listForGroup(String teacherUid, Long groupId) {
        TeacherGroup group = groupService.requireOwnedGroup(teacherUid, groupId);
        cancelAcceptedInvitationsWithoutActiveMembership(group);
        reconcileAcceptedDuplicates(group);
        return invitationRepository.findByGroupOrderByCreatedAtDesc(group)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public GroupInvitationResponse cancel(String teacherUid, Long groupId, Long invitationId) {
        TeacherGroup group = groupService.requireOwnedGroup(teacherUid, groupId);
        GroupInvitation invitation = invitationRepository.findByIdAndGroup(invitationId, group)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zaproszenie nie istnieje."));

        if (invitation.getStatus() == GroupInvitationStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nie można anulować zaakceptowanego zaproszenia.");
        }

        invitation.setStatus(GroupInvitationStatus.CANCELLED);
        return toResponse(invitationRepository.save(invitation));
    }

    @Transactional
    public GroupInvitationResponse resend(String teacherUid, Long groupId, Long invitationId) {
        TeacherGroup group = groupService.requireOwnedGroup(teacherUid, groupId);
        GroupInvitation invitation = invitationRepository.findByIdAndGroup(invitationId, group)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zaproszenie nie istnieje."));

        if (invitation.getStatus() == GroupInvitationStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "To zaproszenie zostało już zaakceptowane.");
        }
        if (hasActiveAcceptedInvitation(invitationsForEmail(group, invitation.getEmail()))
                || checkExistingStudent(invitation.getEmail(), group) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ten uczeń jest już w grupie.");
        }

        Instant now = Instant.now();
        Instant availableAt = latestResendAvailableAt(invitationsForEmail(group, invitation.getEmail()));
        if (invitation.getStatus() != GroupInvitationStatus.FAILED && availableAt != null && now.isBefore(availableAt)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Zaproszenie można ponowić po upływie 24 godzin od ostatniej wysyłki.");
        }

        try {
            sendInvitation(invitation, group);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, GENERIC_INVITE_ERROR);
        }
        return toResponse(invitation);
    }

    @Transactional(readOnly = true)
    public GroupInvitation requirePendingByRawToken(String rawToken) {
        GroupInvitation invitation = invitationRepository.findByTokenHash(hashService.sha256(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "To zaproszenie jest nieprawidłowe albo wygasło."));

        if (invitation.getStatus() == GroupInvitationStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "To zaproszenie zostało już wykorzystane.");
        }

        if (invitation.getStatus() != GroupInvitationStatus.PENDING || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "To zaproszenie jest nieprawidłowe albo wygasło.");
        }

        return invitation;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private GroupInvitationResponse toResponse(GroupInvitation invitation) {
        String message = switch (invitation.getStatus()) {
            case PENDING -> invitation.getExpiresAt().isBefore(Instant.now())
                    ? "Zaproszenie wygasło."
                    : "Oczekuje na akceptację.";
            case ACCEPTED -> "Uczeń zaakceptował zaproszenie.";
            case FAILED -> "Nie udało się wysłać wiadomości.";
            case CANCELLED -> "Zaproszenie anulowane.";
            case EXPIRED -> "Zaproszenie wygasło.";
        };
        return new GroupInvitationResponse(
                invitation.getId(),
                invitation.getGroup().getId(),
                invitation.getGroup().getName(),
                invitation.getEmail(),
                invitation.getStatus(),
                invitation.getCreatedAt(),
                lastSentAt(invitation),
                latestResendAvailableAt(invitationsForEmail(invitation.getGroup(), invitation.getEmail())),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                message
        );
    }

    private Instant lastSentAt(GroupInvitation invitation) {
        return invitation.getLastSentAt();
    }

    private Instant resendAvailableAt(GroupInvitation invitation) {
        Instant lastSentAt = lastSentAt(invitation);
        return lastSentAt == null ? null : lastSentAt.plus(RESEND_COOLDOWN_HOURS, ChronoUnit.HOURS);
    }

    private List<GroupInvitation> invitationsForEmail(TeacherGroup group, String email) {
        return invitationRepository.findByGroupAndEmailIgnoreCaseOrderByCreatedAtDesc(group, email)
                .stream()
                .toList();
    }

    private boolean hasActiveAcceptedInvitation(List<GroupInvitation> invitations) {
        return invitations.stream()
                .filter(invitation -> invitation.getStatus() == GroupInvitationStatus.ACCEPTED)
                .anyMatch(invitation -> invitation.getAcceptedBy() != null
                        && membershipRepository.existsByGroupAndStudentAndStatus(
                        invitation.getGroup(),
                        invitation.getAcceptedBy(),
                        GroupMembershipStatus.ACTIVE
                ));
    }

    private void sendInvitation(GroupInvitation invitation, TeacherGroup group) {
        String rawToken = generateRawToken();
        Instant now = Instant.now();
        invitation.setTokenHash(hashService.sha256(rawToken));
        invitation.setStatus(GroupInvitationStatus.PENDING);
        invitation.setExpiresAt(now.plus(7, ChronoUnit.DAYS));
        if (invitation.getCreatedAt() == null) {
            invitation.setCreatedAt(now);
        }
        if (invitation.getCreatedByTeacher() == null) {
            invitation.setCreatedByTeacher(group.getTeacher());
        }
        String inviteLink = frontendBaseUrl + "/invite/group?token=" + rawToken;
        try {
            emailService.sendGroupInvitation(invitation.getEmail(), group.getName(), group.getTeacher().getName(), inviteLink);
            invitation.setLastSentAt(Instant.now());
            invitation.setStatus(GroupInvitationStatus.PENDING);
            invitation = invitationRepository.save(invitation);
            cancelDuplicatePendingInvitations(invitation);
        } catch (RuntimeException ex) {
            invitation.setStatus(GroupInvitationStatus.FAILED);
            invitationRepository.save(invitation);
            throw ex;
        }
    }

    private boolean isCoolingDown(GroupInvitation invitation) {
        Instant availableAt = resendAvailableAt(invitation);
        return availableAt != null && Instant.now().isBefore(availableAt);
    }

    private String cooldownLabel(Instant availableAt) {
        long minutes = Math.max(1, ChronoUnit.MINUTES.between(Instant.now(), availableAt));
        long hours = minutes / 60;
        long restMinutes = minutes % 60;
        if (hours <= 0) {
            return restMinutes + " min";
        }
        if (restMinutes == 0) {
            return hours + " godz.";
        }
        return hours + " godz. " + restMinutes + " min";
    }

    private Instant latestResendAvailableAt(List<GroupInvitation> invitations) {
        return invitations.stream()
                .map(this::resendAvailableAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
    }

    private void cancelDuplicatePendingInvitations(GroupInvitation activeInvitation) {
        if (activeInvitation.getEmail() == null || activeInvitation.getEmail().isBlank()) {
            return;
        }

        invitationRepository
                .findByGroupAndEmailIgnoreCaseOrderByCreatedAtDesc(
                        activeInvitation.getGroup(),
                        activeInvitation.getEmail()
                )
                .stream()
                .filter(invitation -> !invitation.getId().equals(activeInvitation.getId()))
                .filter(invitation -> invitation.getStatus() == GroupInvitationStatus.PENDING)
                .forEach(invitation -> {
                    invitation.setStatus(GroupInvitationStatus.CANCELLED);
                    invitationRepository.save(invitation);
                });
    }

    private void reconcileAcceptedDuplicates(TeacherGroup group) {
        List<GroupInvitation> invitations = invitationRepository.findByGroupOrderByCreatedAtDesc(group);
        invitations.stream()
                .filter(invitation -> invitation.getEmail() != null && !invitation.getEmail().isBlank())
                .filter(invitation -> invitation.getStatus() == GroupInvitationStatus.ACCEPTED)
                .forEach(accepted -> invitations.stream()
                        .filter(candidate -> !candidate.getId().equals(accepted.getId()))
                        .filter(candidate -> candidate.getStatus() == GroupInvitationStatus.PENDING)
                        .filter(candidate -> candidate.getEmail() != null
                                && candidate.getEmail().equalsIgnoreCase(accepted.getEmail()))
                        .forEach(candidate -> {
                            candidate.setStatus(GroupInvitationStatus.ACCEPTED);
                            candidate.setAcceptedAt(accepted.getAcceptedAt());
                            candidate.setAcceptedBy(accepted.getAcceptedBy());
                            invitationRepository.save(candidate);
                        }));
    }

    private void cancelAcceptedInvitationsWithoutActiveMembership(TeacherGroup group) {
        invitationRepository.findByGroupOrderByCreatedAtDesc(group)
                .stream()
                .filter(invitation -> invitation.getStatus() == GroupInvitationStatus.ACCEPTED)
                .filter(invitation -> invitation.getAcceptedBy() == null
                        || !membershipRepository.existsByGroupAndStudentAndStatus(
                        group,
                        invitation.getAcceptedBy(),
                        GroupMembershipStatus.ACTIVE
                ))
                .forEach(invitation -> {
                    invitation.setStatus(GroupInvitationStatus.CANCELLED);
                    invitation.setAcceptedAt(null);
                    invitation.setAcceptedBy(null);
                    invitationRepository.save(invitation);
                });
    }
}
