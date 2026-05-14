package com.notus.backend.quiz.dto;

import com.notus.backend.quiz.QuizQuestion;

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
        List<QuizQuestion> questions,
        boolean hasSubmissions
) {}
