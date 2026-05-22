package com.notus.backend.quiz.dto;

import com.notus.backend.quiz.QuestionType;

import java.util.List;

public record QuizQuestionDto(
        Long id,
        QuestionType type,
        String questionText,
        List<String> options,
        String correctAnswer
) {}
