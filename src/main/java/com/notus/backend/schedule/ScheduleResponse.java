package com.notus.backend.schedule;

import java.time.Instant;

public record ScheduleResponse(
        String id,
        Instant date,
        String time,
        String subject,
        String teacher,
        Long teacherId,
        String type,
        String room,
        String color,
        Long studentGroupId,
        String studentGroupName,
        Long teacherGroupId,
        boolean recurring,
        String recurrenceSeriesId,
        Integer repeatEveryWeeks,
        Instant recurrenceEndsAt
) {
    public static ScheduleResponse from(Schedule schedule) {
        if (schedule == null) {
            return null;
        }
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getDate(),
                schedule.getTime(),
                schedule.getSubject(),
                schedule.getTeacher(),
                schedule.getTeacherEntity() != null ? schedule.getTeacherEntity().getId() : null,
                schedule.getType(),
                schedule.getRoom(),
                schedule.getColor(),
                schedule.getStudentGroup() != null ? schedule.getStudentGroup().getId() : null,
                schedule.getStudentGroupName(),
                schedule.getTeacherGroupId(),
                schedule.getRecurrenceSeriesId() != null && !schedule.getRecurrenceSeriesId().isBlank(),
                schedule.getRecurrenceSeriesId(),
                schedule.getRepeatEveryWeeks(),
                schedule.getRecurrenceEndsAt()
        );
    }
}
