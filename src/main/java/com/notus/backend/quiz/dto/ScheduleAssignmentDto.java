package com.notus.backend.quiz.dto;

public record ScheduleAssignmentDto(Long assignmentId, String scheduleId, String quizTitle, boolean active, boolean locked) {}
