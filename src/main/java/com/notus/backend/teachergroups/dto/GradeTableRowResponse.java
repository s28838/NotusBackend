package com.notus.backend.teachergroups.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GradeTableRowResponse(
        Long id,
        LocalDate date,
        LocalDateTime dateTime,
        String value,
        double numericValue,
        Integer weight,
        String sourceType,
        Long sourceId,
        String source,
        String comment
) {}
