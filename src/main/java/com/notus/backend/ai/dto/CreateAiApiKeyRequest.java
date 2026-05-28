package com.notus.backend.ai.dto;

import com.notus.backend.ai.AiProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAiApiKeyRequest(
        @NotNull AiProvider provider,
        @Size(max = 120) String label,
        @NotBlank @Size(min = 8, max = 4096) String apiKey
) {}
