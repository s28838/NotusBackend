package com.notus.backend.teachergroups.dto;

import java.time.LocalDate;

public record GradeTableRowResponse(
        Long id,
        LocalDate date,
        String value,
        double numericValue,
        Integer weight,
        String sourceType,
        Long sourceId,
        String source,
        String comment
) {}
