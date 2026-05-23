package com.notus.backend.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, String> {

    List<Schedule> findByDateBetweenAndTeacherEntityNameContainingIgnoreCaseOrderByTimeAsc(
            Instant start,
            Instant end,
            String teacherName
    );

    List<Schedule> findByDateBetweenAndTeacherEntityIdOrderByTimeAsc(
            Instant start,
            Instant end,
            Long teacherId
    );

    List<Schedule> findByDateBetweenAndStudentGroupIdOrderByTimeAsc(
            Instant start,
            Instant end,
            Long groupId
    );

    List<Schedule> findByDateBetweenAndTeacherGroup_IdOrderByTimeAsc(
            Instant start,
            Instant end,
            Long groupId
    );

    List<Schedule> findByStudentGroupIdInOrderByDateAscTimeAsc(List<Long> groupIds);

    List<Schedule> findByTeacherGroup_IdInOrderByDateAscTimeAsc(List<Long> groupIds);

    List<Schedule> findByDateBetweenOrderByTimeAsc(Instant start, Instant end);

    List<Schedule> findByDateBetweenAndStudentGroupIdInOrderByTimeAsc(
            Instant start,
            Instant end,
            List<Long> groupIds
    );

    List<Schedule> findByDateBetweenAndTeacherGroup_IdInOrderByTimeAsc(
            Instant start,
            Instant end,
            List<Long> groupIds
    );

    List<Schedule> findByRecurrenceSeriesIdAndTeacherEntityAndDateGreaterThanEqualOrderByDateAscTimeAsc(
            String recurrenceSeriesId,
            com.notus.backend.users.Teacher teacherEntity,
            Instant date
    );

    long countByStudentGroup(com.notus.backend.attendance.group.StudentGroup group);
    long countByTeacherGroup(com.notus.backend.teachergroups.TeacherGroup group);
}
