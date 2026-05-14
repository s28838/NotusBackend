package com.notus.backend.grades.dto;

import java.time.LocalDate;

public record UpdateGradeRequest(
        String value,
        Integer weight,
        String semester,
        String title,
        String description,
        String comment,
        LocalDate gradeDate
) {}
