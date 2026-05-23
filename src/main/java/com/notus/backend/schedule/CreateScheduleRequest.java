package com.notus.backend.schedule;

import java.time.Instant;

public record CreateScheduleRequest(
        String subject,
        Instant date,
        String time,
        String room,
        String type,
        Long studentGroupId,
        Long teacherGroupId,
        String color,
        Boolean repeatWeekly,
        Integer repeatEveryWeeks,
        Instant repeatUntil
) {}
