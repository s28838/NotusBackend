package com.notus.backend.ai.dto;

import com.notus.backend.ai.AiProvider;

import java.time.Instant;

public record TeacherAiApiKeyResponse(
        Long id,
        AiProvider provider,
        String label,
        String keyPreview,
        Instant createdAt
) {}
