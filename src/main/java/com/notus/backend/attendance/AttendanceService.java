package com.notus.backend.attendance;

import com.notus.backend.attendance.dto.*;
import com.notus.backend.realtime.TeacherRealtimeService;
import com.notus.backend.realtime.dto.TeacherRealtimeEvent;
import com.notus.backend.schedule.Schedule;
import com.notus.backend.schedule.ScheduleRepository;
import com.notus.backend.teachergroups.GroupMembershipRepository;
import com.notus.backend.teachergroups.GroupMembershipStatus;
import com.notus.backend.users.Student;
import com.notus.backend.users.StudentRepository;
import com.notus.backend.users.TeacherRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepo;
    private final AttendanceRecordRepository recordRepo;
    private final QrTokenService qrTokenService;
    private final QrImageService qrImageService;
    private final StudentRepository studentRepo;
    private final TeacherRepository teacherRepo;
    private final ScheduleRepository scheduleRepository;
    private final TeacherRealtimeService realtimeService;
    private final GroupMembershipRepository groupMembershipRepository;

    public AttendanceService(
            AttendanceSessionRepository sessionRepo,
            AttendanceRecordRepository recordRepo,
            QrTokenService qrTokenService,
            QrImageService qrImageService,
            StudentRepository studentRepo,
            TeacherRepository teacherRepo,
            ScheduleRepository scheduleRepository,
            TeacherRealtimeService realtimeService,
            GroupMembershipRepository groupMembershipRepository
    ) {
        this.sessionRepo = sessionRepo;
        this.recordRepo = recordRepo;
        this.qrTokenService = qrTokenService;
        this.qrImageService = qrImageService;
        this.studentRepo = studentRepo;
        this.teacherRepo = teacherRepo;
        this.scheduleRepository = scheduleRepository;
        this.realtimeService = realtimeService;
        this.groupMembershipRepository = groupMembershipRepository;
    }

    @Transactional
    public CreateSessionResponse createSession(String teacherUid, CreateSessionRequest req) {
        log.info("Request: Create session for teacher {} and schedule {}", teacherUid, req != null ? req.scheduleId() : "null");

        if (req == null || req.scheduleId() == null || req.scheduleId().isBlank()) {
            log.error("Failed to create session: Missing scheduleId");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak scheduleId");
        }

        var teacher = teacherRepo.findByClerkUserId(teacherUid)
                .orElseThrow(() -> {
                    log.error("Teacher not found: {}", teacherUid);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Nauczyciel nie znaleziony");
                });

        Schedule schedule = scheduleRepository.findById(req.scheduleId())
                .orElseThrow(() -> {
                    log.error("Schedule not found: {}", req.scheduleId());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono wpisu planu");
                });

        if (schedule.getTeacherEntity() == null || !schedule.getTeacherEntity().getId().equals(teacher.getId())) {
            log.warn("Access Denied: Teacher {} tried to create session for foreign schedule {}", teacherUid, schedule.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nie możesz utworzyć sesji dla cudzych zajęć");
        }

        AttendanceSession session = new AttendanceSession();
        session.setTeacher(teacher);
        session.setSchedule(schedule);
        session.setShortCode(generateShortCode());
        session.setActive(true);
        session.setTitle(schedule.getSubject());
        session.setEndsAt(computeEndsAt(schedule));

        Instant createdAt = schedule.getDate() != null ? schedule.getDate() : Instant.now();
        session.setCreatedAt(createdAt);

        session = sessionRepo.save(session);
        log.info("Successfully created session ID: {} with code: {}", session.getId(), session.getShortCode());

        return new CreateSessionResponse(
                session.getId(),
                schedule.getId(),
                schedule.getSubject(),
                schedule.getRoom(),
                schedule.getTime(),
                session.getCreatedAt(),
                session.isActive()
        );
    }

    @Transactional(readOnly = true)
    public QrResponse generateQr(String teacherUid, Long sessionId) {
        log.debug("Generating QR for session {} by teacher {}", sessionId, teacherUid);
        var teacher = teacherRepo.findByClerkUserId(teacherUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nauczyciel nie znaleziony"));

        AttendanceSession s = sessionRepo.findByIdAndTeacher(sessionId, teacher)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesja nie istnieje"));

        if (!s.isActive()) {
            log.warn("QR request rejected: Session {} is inactive", sessionId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sesja nieaktywna");
        }

        if (s.getShortCode() == null || s.getShortCode().isBlank()) {
            s.setShortCode(generateShortCode());
            s = sessionRepo.save(s);
        }

        String qrToken = qrTokenService.createToken(s.getId());
        long expiresAt = Instant.now().getEpochSecond() + qrTokenService.ttlSeconds();
        String pngBase64 = qrImageService.toPngBase64(qrToken, 320);

        long sessionEndsAt = s.getEndsAt() != null ? s.getEndsAt().getEpochSecond() : expiresAt;
        return new QrResponse(s.getId(), qrToken, pngBase64, expiresAt, s.getShortCode(), sessionEndsAt);
    }

    @Transactional
    public CheckInResponse checkIn(String studentUid, CheckInRequest req) {
        log.info("Student {} attempt check-in", studentUid);

        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak danych check-in");
        }

        AttendanceSession s;
        if (req.qrToken() != null && !req.qrToken().isBlank()) {
            var data = qrTokenService.verifyAndParse(req.qrToken());
            s = sessionRepo.findById(data.sessionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesja nie istnieje"));
        } else if (req.shortCode() != null && !req.shortCode().isBlank()) {
            s = sessionRepo.findByShortCode(req.shortCode().trim().toUpperCase())
                    .orElseThrow(() -> {
                        log.warn("Invalid short code attempt: {} by student {}", req.shortCode(), studentUid);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Kod niepoprawny");
                    });
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak qrToken lub shortCode");
        }

        if (!s.isActive()) {
            log.warn("Check-in rejected: Session {} is inactive", s.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sesja nieaktywna");
        }

        var student = studentRepo.findByClerkUserId(studentUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student nie znaleziony"));
        assertStudentCanAttend(s, student);

        var existing = recordRepo.findBySessionIdAndStudent(s.getId(), student);

        if (existing.isPresent()) {
            log.info("Student {} already checked in for session {}", studentUid, s.getId());
            AttendanceRecord existingRecord = existing.get();
            return new CheckInResponse(existingRecord.getSessionId(), subjectOf(s), studentUid, student.getName(), student.getIndexNumber(), existingRecord.getCheckedInAt(), true, s.getEndsAt());
        }

        AttendanceRecord r = new AttendanceRecord();
        r.setSessionId(s.getId());
        r.setStudent(student);
        r.setCheckedInAt(Instant.now());

        r = recordRepo.save(r);
        log.info("Student {} successfully checked in for session {}", studentUid, s.getId());

        String subject = subjectOf(s);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", s.getId());
        if (s.getSchedule() != null) {
            payload.put("scheduleId", s.getSchedule().getId());
        }
        payload.put("subject", subject);
        payload.put("studentId", student.getId());
        payload.put("studentName", student.getName());
        payload.put("studentIndex", student.getIndexNumber());
        payload.put("checkedInAt", r.getCheckedInAt().toString());
        payload.values().removeIf(Objects::isNull);

        if (s.getTeacher() != null && s.getTeacher().getClerkUserId() != null) {
            realtimeService.publishToTeacher(
                    s.getTeacher().getClerkUserId(),
                    "attendance.checked_in",
                    TeacherRealtimeEvent.of("attendance.checked_in", payload)
            );
        }

        return new CheckInResponse(r.getSessionId(), subject, studentUid, student.getName(), student.getIndexNumber(), r.getCheckedInAt(), false, s.getEndsAt());
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> getRecordsForSession(String teacherUid, Long sessionId) {
        var teacher = teacherRepo.findByClerkUserId(teacherUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nauczyciel nie znaleziony"));

        AttendanceSession session = sessionRepo.findByIdAndTeacher(sessionId, teacher)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesja nie istnieje"));

        return recordRepo.findBySessionId(sessionId)
                .stream()
                .map(r -> {
                    Student student = r.getStudent();
                    return new CheckInResponse(r.getSessionId(), session.getSchedule().getSubject(), student.getClerkUserId(), student.getName(), student.getIndexNumber(), r.getCheckedInAt(), false, session.getSchedule().getDate());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendanceSessionSummaryDto getSessionSummary(String teacherUid, Long sessionId) {
        var teacher = teacherRepo.findByClerkUserId(teacherUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nauczyciel nie znaleziony"));

        AttendanceSession session = sessionRepo.findByIdAndTeacher(sessionId, teacher)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesja nie istnieje"));

        Schedule schedule = session.getSchedule();
        return new AttendanceSessionSummaryDto(
                session.getId(),
                groupSessionNumber(session, teacher),
                subjectOf(session),
                schedule != null ? schedule.getId() : null,
                groupName(schedule),
                session.getCreatedAt(),
                session.isActive()
        );
    }

    /**
     * Parses the end time from a schedule time string ("HH:mm - HH:mm") and
     * combines it with the schedule date to produce a wall-clock Instant in
     * Europe/Warsaw timezone. Returns null if the time string is null, blank,
     * or unparseable.
     */
    private Instant computeEndsAt(Schedule schedule) {
        if (schedule.getTime() == null || schedule.getTime().isBlank()) {
            return null;
        }
        if (schedule.getDate() == null) {
            return null;
        }
        try {
            String[] parts = schedule.getTime().split(" - ");
            if (parts.length < 2) return null;
            String[] hm = parts[1].trim().split(":");
            if (hm.length < 2) return null;
            int hour = Integer.parseInt(hm[0].trim());
            int minute = Integer.parseInt(hm[1].trim());

            java.time.ZoneId warsaw = java.time.ZoneId.of("Europe/Warsaw");
            java.time.LocalDate day = schedule.getDate()
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate();
            return day.atTime(hour, minute)
                    .atZone(warsaw)
                    .toInstant();
        } catch (Exception e) {
            log.warn("Could not parse endsAt from schedule time '{}': {}", schedule.getTime(), e.getMessage());
            return null;
        }
    }

    private String subjectOf(AttendanceSession session) {
        if (session.getSchedule() != null) {
            return session.getSchedule().getSubject();
        }
        return session.getTitle();
    }

    private Integer groupSessionNumber(AttendanceSession targetSession, com.notus.backend.users.Teacher teacher) {
        String targetGroupKey = groupKey(targetSession);
        if (targetGroupKey == null) {
            return 1;
        }

        List<AttendanceSession> groupSessions = sessionRepo.findByTeacher(teacher).stream()
                .filter(session -> targetGroupKey.equals(groupKey(session)))
                .sorted(Comparator
                        .comparing(this::sessionSortInstant, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(session -> session.getId() != null ? session.getId() : Long.MAX_VALUE))
                .toList();

        for (int i = 0; i < groupSessions.size(); i++) {
            if (Objects.equals(groupSessions.get(i).getId(), targetSession.getId())) {
                return i + 1;
            }
        }

        return groupSessions.size() + 1;
    }

    private Instant sessionSortInstant(AttendanceSession session) {
        if (session.getSchedule() != null && session.getSchedule().getDate() != null) {
            return session.getSchedule().getDate();
        }
        return session.getCreatedAt();
    }

    private String groupKey(AttendanceSession session) {
        Schedule schedule = session.getSchedule();
        if (schedule == null) {
            return null;
        }
        if (schedule.getTeacherGroup() != null && schedule.getTeacherGroup().getId() != null) {
            return "teacher-group:" + schedule.getTeacherGroup().getId();
        }
        if (schedule.getStudentGroup() != null && schedule.getStudentGroup().getId() != null) {
            return "student-group:" + schedule.getStudentGroup().getId();
        }
        if (schedule.getId() != null) {
            return "schedule:" + schedule.getId();
        }
        return null;
    }

    private String groupName(Schedule schedule) {
        if (schedule == null) {
            return null;
        }
        if (schedule.getTeacherGroup() != null) {
            return schedule.getTeacherGroup().getName();
        }
        if (schedule.getStudentGroup() != null) {
            return schedule.getStudentGroup().getCode();
        }
        return null;
    }

    private void assertStudentCanAttend(AttendanceSession session, Student student) {
        Schedule schedule = session.getSchedule();
        if (schedule == null) {
            return;
        }
        if (schedule.getTeacherGroup() != null) {
            boolean member = groupMembershipRepository
                    .findByGroupAndStudentAndStatus(schedule.getTeacherGroup(), student, GroupMembershipStatus.ACTIVE)
                    .isPresent();
            if (!member) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nie należysz do grupy tych zajęć.");
            }
        }
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateShortCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return code.toString();
    }

    @Transactional
    public void closeSession(String teacherUid, Long sessionId) {
        log.info("Teacher {} is closing session {}", teacherUid, sessionId);

        // 1. Znajdź nauczyciela
        var teacher = teacherRepo.findByClerkUserId(teacherUid)
                .orElseThrow(() -> {
                    log.error("Teacher not found: {}", teacherUid);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Nauczyciel nie znaleziony");
                });

        // 2. Znajdź sesję (findByIdAndTeacher gwarantuje, że nikt nie zamknie cudzej sesji)
        AttendanceSession session = sessionRepo.findByIdAndTeacher(sessionId, teacher)
                .orElseThrow(() -> {
                    log.error("Session {} not found or doesn't belong to teacher {}", sessionId, teacherUid);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesja nie istnieje lub brak uprawnień");
                });

        // 3. Jeśli już zamknięta, nie ma sensu nic robić (można zalogować warn)
        if (!session.isActive()) {
            log.warn("Session {} is already closed", sessionId);
            return;
        }

        // 4. Zamknij i zapisz
        session.setActive(false);
        sessionRepo.save(session);
        log.info("Session {} successfully closed by teacher {}", sessionId, teacherUid);
    }
}
