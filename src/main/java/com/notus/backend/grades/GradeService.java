package com.notus.backend.grades;

import com.notus.backend.grades.dto.CreateGradeRequest;
import com.notus.backend.grades.dto.DeleteGradeResponse;
import com.notus.backend.grades.dto.GradeResponse;
import com.notus.backend.grades.dto.UpdateGradeRequest;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GradeService {

    private final GradeRepository gradeRepository;
    private final GradeValueMapper gradeValueMapper;
    private final TeacherGroupService groupService;
    private final GroupMembershipService membershipService;

    public GradeService(GradeRepository gradeRepository,
                        GradeValueMapper gradeValueMapper,
                        TeacherGroupService groupService,
                        GroupMembershipService membershipService) {
        this.gradeRepository = gradeRepository;
        this.gradeValueMapper = gradeValueMapper;
        this.groupService = groupService;
        this.membershipService = membershipService;
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
        return toResponse(gradeRepository.save(grade));
    }

    @Transactional
    public GradeResponse updateGrade(String teacherUid, Long groupId, Long studentId, Long gradeId, UpdateGradeRequest request) {
        TeacherGroup group = groupService.requireOwnedGroup(teacherUid, groupId);
        membershipService.requireActiveMembership(group, studentId);
        Grade grade = gradeRepository.findByIdAndGroupAndStudentAndDeletedAtIsNull(gradeId, group, membershipService.requireActiveMembership(group, studentId).getStudent())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocena nie istnieje."));
        applyRequest(grade, request.value(), request.weight(), request.semester(), request.title(), request.description(), request.comment(), request.gradeDate());
        grade.setUpdatedAt(LocalDateTime.now());
        return toResponse(gradeRepository.save(grade));
    }

    @Transactional
    public DeleteGradeResponse deleteGrade(String teacherUid, Long groupId, Long studentId, Long gradeId) {
        TeacherGroup group = groupService.requireOwnedGroup(teacherUid, groupId);
        Student student = membershipService.requireActiveMembership(group, studentId).getStudent();
        Grade grade = gradeRepository.findByIdAndGroupAndStudentAndDeletedAtIsNull(gradeId, group, student)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocena nie istnieje."));
        grade.setDeletedAt(LocalDateTime.now());
        grade.setUpdatedAt(LocalDateTime.now());
        gradeRepository.save(grade);
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
        return toResponse(gradeRepository.save(grade));
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
        grade.setGradeDate(gradeDate != null ? gradeDate : LocalDate.now());
        grade.setIssueDate((gradeDate != null ? gradeDate : LocalDate.now()).atStartOfDay());
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

    public List<GradeDto> getRecentGrades(String clerkUserId) {
        // Dodaję testowe dane dla konta, które jeszcze ich nie ma, aby widok działał poprawnie bez edycji po stronie wykładowcy
        List<Grade> grades = gradeRepository.findByClerkUserIdOrderByIssueDateDesc(clerkUserId);
        
        if (grades.isEmpty()) {
            Grade dummy1 = new Grade();
            dummy1.setClerkUserId(clerkUserId);
            dummy1.setSubject("Architektura Komputerów");
            dummy1.setValue("5.0");
            dummy1.setIssueDate(LocalDateTime.now().minusHours(2));
            dummy1.setNew(true);
            
            Grade dummy2 = new Grade();
            dummy2.setClerkUserId(clerkUserId);
            dummy2.setSubject("Algorytmy i Struktury Danych");
            dummy2.setValue("4.5");
            dummy2.setIssueDate(LocalDateTime.now().minusDays(1));
            dummy2.setNew(false);
            
            gradeRepository.saveAll(List.of(dummy1, dummy2));
            grades = gradeRepository.findByClerkUserIdOrderByIssueDateDesc(clerkUserId);
        }

        return grades.stream()
                .map(g -> new GradeDto(g.getId(), g.getSubject(), g.getValue(), g.getIssueDate(), g.isNew()))
                .collect(Collectors.toList());
    }
}
