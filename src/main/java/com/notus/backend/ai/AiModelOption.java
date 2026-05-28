package com.notus.backend.ai;

public record AiModelOption(
        AiProvider provider,
        String model,
        String label
) {}
