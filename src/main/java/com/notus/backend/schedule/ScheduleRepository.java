package com.notus.backend.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, String> {

    List<Schedule> findByDateBetweenAndTeacherEntityNameContainingIgnoreCaseAndDeletedFalseOrderByTimeAsc(
            Instant start,
            Instant end,
            String teacherName
    );

    List<Schedule> findByDateBetweenAndTeacherEntityIdAndDeletedFalseOrderByTimeAsc(
            Instant start,
            Instant end,
            Long teacherId
    );

    List<Schedule> findByDateBetweenAndStudentGroupIdAndDeletedFalseOrderByTimeAsc(
            Instant start,
            Instant end,
            Long groupId
    );

    List<Schedule> findByDateBetweenAndTeacherGroup_IdAndDeletedFalseOrderByTimeAsc(
            Instant start,
            Instant end,
            Long groupId
    );

    List<Schedule> findByStudentGroupIdInAndDeletedFalseOrderByDateAscTimeAsc(List<Long> groupIds);

    List<Schedule> findByTeacherGroup_IdInAndDeletedFalseOrderByDateAscTimeAsc(List<Long> groupIds);

    List<Schedule> findByDateBetweenAndDeletedFalseOrderByTimeAsc(Instant start, Instant end);

    List<Schedule> findByDateBetweenAndStudentGroupIdInAndDeletedFalseOrderByTimeAsc(
            Instant start,
            Instant end,
            List<Long> groupIds
    );

    List<Schedule> findByDateBetweenAndTeacherGroup_IdInAndDeletedFalseOrderByTimeAsc(
            Instant start,
            Instant end,
            List<Long> groupIds
    );

    List<Schedule> findByRecurrenceSeriesIdAndTeacherEntityAndDateGreaterThanEqualAndDeletedFalseOrderByDateAscTimeAsc(
            String recurrenceSeriesId,
            com.notus.backend.users.Teacher teacherEntity,
            Instant date
    );

    long countByStudentGroupAndDeletedFalse(com.notus.backend.attendance.group.StudentGroup group);
    long countByTeacherGroupAndDeletedFalse(com.notus.backend.teachergroups.TeacherGroup group);
}
