package com.notus.backend.grades;

import com.notus.backend.grades.dto.CreateGradeRequest;
import com.notus.backend.grades.dto.DeleteGradeResponse;
import com.notus.backend.grades.dto.GradeResponse;
import com.notus.backend.grades.dto.UpdateGradeRequest;
import com.notus.backend.realtime.TeacherRealtimeService;
import com.notus.backend.realtime.dto.TeacherRealtimeEvent;
import com.notus.backend.teachergroups.GroupMembershipService;
import com.notus.backend.teachergroups.TeacherGroup;
import com.notus.backend.teachergroups.TeacherGroupService;
import com.notus.backend.users.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class GradeService {

    private final GradeRepository gradeRepository;
    private final GradeValueMapper gradeValueMapper;
    private final TeacherGroupService groupService;
    private final GroupMembershipService membershipService;
    private final TeacherRealtimeService realtimeService;

    public GradeService(GradeRepository gradeRepository,
                        GradeValueMapper gradeValueMapper,
                        TeacherGroupService groupService,
                        GroupMembershipService membershipService,
                        TeacherRealtimeService realtimeService) {
        this.gradeRepository = gradeRepository;
        this.gradeValueMapper = gradeValueMapper;
        this.groupService = groupService;
        this.membershipService = membershipService;
        this.realtimeService = realtimeService;
    }

    @Transactional
    public GradeResponse createManualGrade(String teacherUid, Long groupId, Long studentId, CreateGradeRequest request) {
        TeacherGroup group = groupService.requireOwnedGroup(teacherUid, groupId);
        Student student = membershipService.requireActiveMembership(group, studentId).getStudent();
        Grade grade = new Grade();
        grade.setStudent(student);
        grade.setTeacher(group.getTeacher());
        grade.setGroup(group);
        grade.setClerkUserId(student.getClerkUserId());
        grade.setSubject(group.getSubject() != null ? group.getSubject() : "");
        grade.setSourceType("MANUAL");
        grade.setSourceId(null);
        applyRequest(grade, request.value(), request.weight(), request.semester(), request.title(), request.description(), request.comment(), request.gradeDate());
        Grade saved = gradeRepository.save(grade);
        publishGradeEvent(saved, "grade.created");
        return toResponse(saved);
    }

    @Transactional
    public GradeResponse updateGrade(String teacherUid, Long groupId, Long studentId, Long gradeId, UpdateGradeRequest request) {
        TeacherGroup group = groupService.requireOwnedGroup(teacherUid, groupId);
        membershipService.requireActiveMembership(group, studentId);
        Grade grade = gradeRepository.findByIdAndGroupAndStudentAndDeletedAtIsNull(gradeId, group, membershipService.requireActiveMembership(group, studentId).getStudent())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocena nie istnieje."));
        applyRequest(grade, request.value(), request.weight(), request.semester(), request.title(), request.description(), request.comment(), request.gradeDate());
        grade.setUpdatedAt(LocalDateTime.now());
        Grade saved = gradeRepository.save(grade);
        publishGradeEvent(saved, "grade.updated");
        return toResponse(saved);
    }

    @Transactional
    public DeleteGradeResponse deleteGrade(String teacherUid, Long groupId, Long studentId, Long gradeId) {
        TeacherGroup group = groupService.requireOwnedGroup(teacherUid, groupId);
        Student student = membershipService.requireActiveMembership(group, studentId).getStudent();
        Grade grade = gradeRepository.findByIdAndGroupAndStudentAndDeletedAtIsNull(gradeId, group, student)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocena nie istnieje."));
        grade.setDeletedAt(LocalDateTime.now());
        grade.setUpdatedAt(LocalDateTime.now());
        Grade saved = gradeRepository.save(grade);
        publishGradeEvent(saved, "grade.deleted");
        return new DeleteGradeResponse(true, "Ocena została usunięta.");
    }

    @Transactional
    public GradeResponse createOrUpdateQuizGrade(TeacherGroup group,
                                                 Student student,
                                                 Long quizId,
                                                 String value,
                                                 Integer weight,
                                                 String semester,
                                                 String title,
                                                 String description,
                                                 String comment) {
        membershipService.requireActiveMembership(group, student.getId());
        Grade grade = gradeRepository.findByStudentAndGroupAndSourceTypeAndSourceIdAndDeletedAtIsNull(student, group, "QUIZ", quizId)
                .orElseGet(Grade::new);
        if (grade.getId() == null) {
            grade.setStudent(student);
            grade.setTeacher(group.getTeacher());
            grade.setGroup(group);
            grade.setClerkUserId(student.getClerkUserId());
            grade.setSubject(group.getSubject() != null ? group.getSubject() : "");
            grade.setSourceType("QUIZ");
            grade.setSourceId(quizId);
        }
        applyRequest(grade, value, weight, semester, title, description, comment, LocalDate.now());
        grade.setUpdatedAt(LocalDateTime.now());
        Grade saved = gradeRepository.save(grade);
        publishGradeEvent(saved, "grade.quiz_saved");
        return toResponse(saved);
    }

    private void applyRequest(Grade grade,
                              String value,
                              Integer weight,
                              String semester,
                              String title,
                              String description,
                              String comment,
                              LocalDate gradeDate) {
        if (weight == null || weight <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waga oceny musi być większa od 0.");
        }
        if (semester == null || semester.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Semestr jest wymagany.");
        }
        if ((title == null || title.isBlank()) && (description == null || description.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj, z czego jest ocena.");
        }

        var numericValue = gradeValueMapper.toNumeric(value);
        grade.setValue(value.trim());
        grade.setNumericValue(numericValue);
        grade.setWeight(weight);
        grade.setSemester(semester.trim());
        grade.setTitle(title);
        grade.setDescription(description);
        grade.setComment(comment);
        LocalDateTime now = LocalDateTime.now();
        LocalDate resolvedGradeDate = gradeDate != null ? gradeDate : now.toLocalDate();
        grade.setGradeDate(resolvedGradeDate);
        grade.setIssueDate(resolvedGradeDate.atTime(now.toLocalTime()));
        if (grade.getCreatedAt() == null) {
            grade.setCreatedAt(LocalDateTime.now());
        }
    }

    public GradeResponse toResponse(Grade grade) {
        return new GradeResponse(
                grade.getId(),
                grade.getStudent() != null ? grade.getStudent().getId() : null,
                grade.getStudent() != null ? grade.getStudent().getName() : null,
                grade.getGroup() != null ? grade.getGroup().getId() : null,
                grade.getTeacher() != null ? grade.getTeacher().getId() : null,
                grade.getValue(),
                grade.getNumericValue(),
                grade.getWeight(),
                grade.getSemester(),
                grade.getSourceType(),
                grade.getSourceId(),
                grade.getTitle(),
                grade.getDescription(),
                grade.getComment(),
                grade.getGradeDate()
        );
    }

    @Transactional(readOnly = true)
    public List<GradeDto> getRecentGrades(String clerkUserId) {
        return gradeRepository.findByClerkUserIdAndDeletedAtIsNullOrderByIssueDateDesc(clerkUserId).stream()
                .map(g -> new GradeDto(
                        g.getId(),
                        g.getGroup() != null ? g.getGroup().getId() : null,
                        g.getGroup() != null ? g.getGroup().getName() : null,
                        g.getSubject(),
                        g.getValue(),
                        g.getIssueDate(),
                        g.isNew()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public void markSeen(String clerkUserId, Long gradeId) {
        Grade grade = gradeRepository.findById(gradeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocena nie istnieje."));
        if (!grade.getClerkUserId().equals(clerkUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nie masz dostępu do tej oceny.");
        }
        grade.setNew(false);
        grade.setUpdatedAt(LocalDateTime.now());
        gradeRepository.save(grade);
    }

    private void publishGradeEvent(Grade grade, String eventName) {
        if (grade.getTeacher() == null || grade.getGroup() == null || grade.getStudent() == null) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("groupId", grade.getGroup().getId());
        payload.put("groupName", grade.getGroup().getName());
        payload.put("studentId", grade.getStudent().getId());
        payload.put("studentName", grade.getStudent().getName());
        payload.put("gradeId", grade.getId());
        payload.put("value", grade.getValue());
        payload.put("sourceType", grade.getSourceType());
        payload.values().removeIf(Objects::isNull);

        realtimeService.publishToTeacher(
                grade.getTeacher().getClerkUserId(),
                eventName,
                TeacherRealtimeEvent.of(eventName, payload)
        );
    }
}
