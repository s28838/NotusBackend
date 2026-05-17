package com.notus.backend.teachergroups;

import com.notus.backend.grades.Grade;
import com.notus.backend.grades.GradeRepository;
import com.notus.backend.users.Role;
import com.notus.backend.users.Student;
import com.notus.backend.users.StudentRepository;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TeacherGroupDevDataSeeder implements CommandLineRunner {

    private static final String DEV_TEACHER_UID = "dev-user-t.kowalski";
    private static final String DEV_TEACHER_EMAIL = "t.kowalski@pwr.edu.pl";
    private static final String DEV_STUDENT_UID = "dev-user-s12345";
    private static final String DEV_STUDENT_EMAIL = "s12345@student.pwr.edu.pl";

    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final TeacherGroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final GradeRepository gradeRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Teacher teacher = teacherRepository.findByClerkUserId(DEV_TEACHER_UID)
                .orElseGet(this::createDevTeacher);
        Student student = studentRepository.findByClerkUserId(DEV_STUDENT_UID)
                .orElseGet(this::createDevStudent);
        TeacherGroup group = findOrCreateMathGroup(teacher);

        GroupMembership membership = membershipRepository.findByGroupAndStudent(group, student)
                .orElseGet(GroupMembership::new);
        membership.setGroup(group);
        membership.setStudent(student);
        membership.setDisplayNameOverride(student.getName());
        membership.setEmailOverride(student.getEmail());
        membership.setStatus(GroupMembershipStatus.ACTIVE);
        membership.setRemovedAt(null);
        membershipRepository.save(membership);

        if (gradeRepository.findByGroupAndStudentAndDeletedAtIsNullOrderByGradeDateDesc(group, student).isEmpty()) {
            seedGrade(group, student, "5", BigDecimal.valueOf(5.0), 1, "2",
                    "Kartkówka", "Funkcje liniowe", "Bardzo dobra praca", LocalDate.now().minusDays(1), true);
            seedGrade(group, student, "4+", BigDecimal.valueOf(4.5), 2, "2",
                    "Sprawdzian", "Równania", "Drobne błędy rachunkowe", LocalDate.now().minusDays(5), false);
            seedGrade(group, student, "5-", BigDecimal.valueOf(4.75), 1, "1",
                    "Odpowiedź ustna", "Geometria", "Pewna odpowiedź", LocalDate.now().minusDays(18), false);
            log.info("Seeded dev grades for {} in {}", DEV_STUDENT_EMAIL, group.getName());
        }
    }

    private Teacher createDevTeacher() {
        Teacher teacher = new Teacher();
        teacher.setClerkUserId(DEV_TEACHER_UID);
        teacher.setEmail(DEV_TEACHER_EMAIL);
        teacher.setName("Dev User (t.kowalski@pwr.edu.pl)");
        teacher.setRole(Role.TEACHER);
        return teacherRepository.save(teacher);
    }

    private Student createDevStudent() {
        Student student = new Student();
        student.setClerkUserId(DEV_STUDENT_UID);
        student.setEmail(DEV_STUDENT_EMAIL);
        student.setName("Dev User (s12345@student.pwr.edu.pl)");
        student.setRole(Role.STUDENT);
        student.setIndexNumber("s12345");
        return studentRepository.save(student);
    }

    private TeacherGroup findOrCreateMathGroup(Teacher teacher) {
        return groupRepository.findByTeacherAndSubjectIgnoreCaseAndActiveTrue(teacher, "Matematyka")
                .stream()
                .filter(group -> group.getName() != null && group.getName().toLowerCase().contains("matematyka 1"))
                .findFirst()
                .orElseGet(() -> {
                    TeacherGroup group = new TeacherGroup();
                    group.setTeacher(teacher);
                    group.setName("Matematyka 1A");
                    group.setDescription("Grupa testowa do sprawdzania widoku ucznia, ocen i frekwencji.");
                    group.setSubject("Matematyka");
                    group.setSchoolYear("2025/2026");
                    group.setSemester("2");
                    group.setActive(true);
                    return groupRepository.save(group);
                });
    }

    private void seedGrade(TeacherGroup group,
                           Student student,
                           String value,
                           BigDecimal numericValue,
                           int weight,
                           String semester,
                           String title,
                           String description,
                           String comment,
                           LocalDate gradeDate,
                           boolean isNew) {
        Grade grade = new Grade();
        grade.setStudent(student);
        grade.setTeacher(group.getTeacher());
        grade.setGroup(group);
        grade.setClerkUserId(student.getClerkUserId());
        grade.setSubject(group.getSubject());
        grade.setValue(value);
        grade.setNumericValue(numericValue);
        grade.setWeight(weight);
        grade.setSemester(semester);
        grade.setSourceType("MANUAL");
        grade.setTitle(title);
        grade.setDescription(description);
        grade.setComment(comment);
        grade.setGradeDate(gradeDate);
        grade.setIssueDate(gradeDate.atStartOfDay());
        grade.setCreatedAt(LocalDateTime.now());
        grade.setNew(isNew);
        gradeRepository.save(grade);
    }
}
