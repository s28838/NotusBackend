package com.notus.backend.teachergroups;

import com.notus.backend.grades.Grade;
import com.notus.backend.grades.GradeRepository;
import com.notus.backend.schedule.Schedule;
import com.notus.backend.schedule.ScheduleRepository;
import com.notus.backend.users.Role;
import com.notus.backend.users.Student;
import com.notus.backend.users.StudentRepository;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "notus.seed.dev-data-enabled", havingValue = "true")
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
    private final ScheduleRepository scheduleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Teacher teacher = teacherRepository.findByClerkUserId(DEV_TEACHER_UID)
                .orElseGet(this::createDevTeacher);
        Student student = studentRepository.findByClerkUserId(DEV_STUDENT_UID)
                .orElseGet(this::createDevStudent);

        TeacherGroup mathGroup = findOrCreateGroup(
                teacher,
                "Matematyka 1A",
                "Matematyka",
                "Grupa testowa do sprawdzania widoku ucznia, ocen i frekwencji."
        );
        TeacherGroup physicsGroup = findOrCreateGroup(
                teacher,
                "Fizyka 2B",
                "Fizyka",
                "Druga grupa testowa do prezentacji planu, obecnosci i quizow."
        );

        ensureMembership(mathGroup, student);
        ensureMembership(physicsGroup, student);
        seedWeekSchedule(mathGroup, physicsGroup);

        if (gradeRepository.findByGroupAndStudentAndDeletedAtIsNullOrderByGradeDateDesc(mathGroup, student).isEmpty()) {
            seedGrade(mathGroup, student, "5", BigDecimal.valueOf(5.0), 1, "2",
                    "Kartkowka", "Funkcje liniowe", "Bardzo dobra praca", LocalDate.now().minusDays(1), true);
            seedGrade(mathGroup, student, "4+", BigDecimal.valueOf(4.5), 2, "2",
                    "Sprawdzian", "Rownania", "Drobne bledy rachunkowe", LocalDate.now().minusDays(5), false);
            seedGrade(mathGroup, student, "5-", BigDecimal.valueOf(4.75), 1, "1",
                    "Odpowiedz ustna", "Geometria", "Pewna odpowiedz", LocalDate.now().minusDays(18), false);
            log.info("Seeded dev grades for {} in {}", DEV_STUDENT_EMAIL, mathGroup.getName());
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

    private TeacherGroup findOrCreateGroup(Teacher teacher, String name, String subject, String description) {
        return groupRepository.findByTeacherAndSubjectIgnoreCaseAndActiveTrue(teacher, subject)
                .stream()
                .filter(group -> name.equalsIgnoreCase(group.getName()))
                .findFirst()
                .orElseGet(() -> {
                    TeacherGroup group = new TeacherGroup();
                    group.setTeacher(teacher);
                    group.setName(name);
                    group.setDescription(description);
                    group.setSubject(subject);
                    group.setSchoolYear("2025/2026");
                    group.setSemester("2");
                    group.setActive(true);
                    return groupRepository.save(group);
                });
    }

    private void ensureMembership(TeacherGroup group, Student student) {
        GroupMembership membership = membershipRepository.findByGroupAndStudent(group, student)
                .orElseGet(GroupMembership::new);
        membership.setGroup(group);
        membership.setStudent(student);
        membership.setDisplayNameOverride(student.getName());
        membership.setEmailOverride(student.getEmail());
        membership.setStatus(GroupMembershipStatus.ACTIVE);
        membership.setRemovedAt(null);
        membershipRepository.save(membership);
    }

    private void seedWeekSchedule(TeacherGroup mathGroup, TeacherGroup physicsGroup) {
        if (scheduleRepository.countByTeacherGroupAndDeletedFalse(mathGroup) > 0
                || scheduleRepository.countByTeacherGroupAndDeletedFalse(physicsGroup) > 0) {
            return;
        }

        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        TeacherGroup[] groups = {mathGroup, physicsGroup, mathGroup, physicsGroup, mathGroup, physicsGroup, mathGroup};
        String[] times = {"08:15 - 09:45", "10:00 - 11:30", "11:45 - 13:15", "14:00 - 15:30", "08:15 - 09:45", "10:00 - 11:30", "12:00 - 13:30"};
        String[] rooms = {"A101", "B202", "C303", "L3", "A102", "B204", "C105"};
        String[] types = {"Cwiczenia", "Laboratorium", "Wyklad", "Cwiczenia", "Laboratorium", "Wyklad", "Konsultacje"};

        for (int i = 0; i < groups.length; i++) {
            TeacherGroup group = groups[i];
            LocalDate day = monday.plusDays(i);
            scheduleRepository.save(Schedule.builder()
                    .id(UUID.randomUUID().toString())
                    .date(day.atTime(LocalTime.NOON).atZone(ZoneId.of("Europe/Warsaw")).toInstant())
                    .time(times[i])
                    .subject(group.getSubject())
                    .teacherEntity(group.getTeacher())
                    .type(types[i])
                    .room(rooms[i])
                    .color(i % 2 == 0 ? "primary" : "emerald")
                    .teacherGroup(group)
                    .build());
        }
        log.info("Seeded alternating week schedule for dev groups");
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
