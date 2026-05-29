package com.notus.backend.attendance.dto;

import java.time.Instant;

public record AttendanceSessionSummaryDto(
        Long sessionId,
        Integer groupSessionNumber,
        String sessionTitle,
        String scheduleId,
        String groupName,
        Instant createdAt,
        boolean active
) {}
