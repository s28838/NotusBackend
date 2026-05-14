package com.notus.backend.grades.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GradeResponse(
        Long id,
        Long studentId,
        String studentName,
        Long groupId,
        Long teacherId,
        String value,
        BigDecimal numericValue,
        Integer weight,
        String semester,
        String sourceType,
        Long sourceId,
        String title,
        String description,
        String comment,
        LocalDate gradeDate
) {}
