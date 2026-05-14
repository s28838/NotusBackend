package com.notus.backend.quiz.dto;

import com.notus.backend.grades.dto.GradeResponse;

public record SubmitResultDto(
        int score,
        int total,
        double percentage,
        boolean gradeCreated,
        GradeResponse grade
) {
    public SubmitResultDto(int score, int total) {
        this(score, total, total == 0 ? 0.0 : Math.round((score * 1000.0) / total) / 10.0, false, null);
    }
}
