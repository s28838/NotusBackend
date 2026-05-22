package com.notus.backend.quiz.dto;

import java.time.Instant;
import java.util.List;

public record QuizDetailsDto(
        Long id,
        String title,
        String description,
        Instant createdAt,
        int version,
        Long groupId,
        boolean countAsGrade,
        Integer gradeWeight,
        String semester,
        List<QuizQuestionDto> questions,
        boolean hasSubmissions
) {}
