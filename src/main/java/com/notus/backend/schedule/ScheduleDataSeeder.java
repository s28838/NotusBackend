package com.notus.backend.schedule;

import com.notus.backend.attendance.group.StudentGroup;
import com.notus.backend.attendance.group.StudentGroupRepository;
import com.notus.backend.attendance.group.StudentGroupType;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "notus.seed.sample-schedule-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ScheduleDataSeeder implements CommandLineRunner {

    private final ScheduleRepository scheduleRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final TeacherRepository teacherRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Running ScheduleDataSeeder...");
        long count = scheduleRepository.count();
        log.info("Current schedule count: {}", count);

        // 1. Create or Get Group
        StudentGroup group = studentGroupRepository.findByCode("INF-2024-SEM2").orElseGet(() -> {
            log.info("Creating default student group INF-2024-SEM2...");
            StudentGroup g = new StudentGroup();
            g.setCode("INF-2024-SEM2");
            g.setStudentGroupType(StudentGroupType.LECTURE);
            return studentGroupRepository.save(g);
        });

        long groupScheduleCount = scheduleRepository.countByStudentGroupAndDeletedFalse(group);
        log.info("Schedule count for group {}: {}", group.getCode(), groupScheduleCount);

        if (groupScheduleCount == 0) {
            log.info("Seeding new schedule entries for group {}...", group.getCode());
            
            // 2. Create Teacher
            Teacher teacher = teacherRepository.findByClerkUserId("seeder-teacher-1").orElseGet(() -> {
                Teacher t = new Teacher();
                t.setClerkUserId("seeder-teacher-1");
                t.setEmail("kowalski@pjwstk.edu.pl");
                t.setName("dr Jan Kowalski");
                return teacherRepository.save(t);
            });

            // 3. Create Schedule entries
            Instant now = Instant.now();

            // Lesson Today
            scheduleRepository.save(Schedule.builder()
                    .id(UUID.randomUUID().toString())
                    .date(now)
                    .time("14:00 - 15:30")
                    .subject("Programowanie Obiektowe")
                    .teacherEntity(teacher)
                    .type("Wykład")
                    .room("A101")
                    .color("primary")
                    .studentGroup(group)
                    .build());

            // Lesson Tomorrow
            scheduleRepository.save(Schedule.builder()
                    .id(UUID.randomUUID().toString())
                    .date(now.plus(1, ChronoUnit.DAYS))
                    .time("08:15 - 09:45")
                    .subject("Bazy Danych")
                    .teacherEntity(teacher)
                    .type("Ćwiczenia")
                    .room("B202")
                    .color("secondary")
                    .studentGroup(group)
                    .build());

            // Lesson in 3 days
            scheduleRepository.save(Schedule.builder()
                    .id(UUID.randomUUID().toString())
                    .date(now.plus(3, ChronoUnit.DAYS))
                    .time("10:00 - 11:30")
                    .subject("Systemy Operacyjne")
                    .teacherEntity(teacher)
                    .type("Laboratorium")
                    .room("L3")
                    .color("success")
                    .studentGroup(group)
                    .build());
        }

        log.info("Sample schedule seeding finished without auto-assigning students to legacy groups.");
    }
}
