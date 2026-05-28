package com.notus.backend.ai;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Component
public class AiModelCatalog {

    private final Map<AiProvider, List<AiModelOption>> models = Map.of(
            AiProvider.OPENAI, List.of(
                    new AiModelOption(AiProvider.OPENAI, "gpt-5.5", "GPT-5.5"),
                    new AiModelOption(AiProvider.OPENAI, "gpt-5.4", "GPT-5.4"),
                    new AiModelOption(AiProvider.OPENAI, "gpt-5.4-mini", "GPT-5.4 mini"),
                    new AiModelOption(AiProvider.OPENAI, "gpt-5.4-nano", "GPT-5.4 nano")
            ),
            AiProvider.ANTHROPIC, List.of(
                    new AiModelOption(AiProvider.ANTHROPIC, "claude-opus-4-1-20250805", "Claude Opus 4.1"),
                    new AiModelOption(AiProvider.ANTHROPIC, "claude-opus-4-20250514", "Claude Opus 4"),
                    new AiModelOption(AiProvider.ANTHROPIC, "claude-sonnet-4-20250514", "Claude Sonnet 4"),
                    new AiModelOption(AiProvider.ANTHROPIC, "claude-3-5-haiku-20241022", "Claude Haiku 3.5")
            ),
            AiProvider.GOOGLE_GEMINI, List.of(
                    new AiModelOption(AiProvider.GOOGLE_GEMINI, "gemini-3.5-flash", "Gemini 3.5 Flash"),
                    new AiModelOption(AiProvider.GOOGLE_GEMINI, "gemini-3.1-pro", "Gemini 3.1 Pro"),
                    new AiModelOption(AiProvider.GOOGLE_GEMINI, "gemini-2.5-flash", "Gemini 2.5 Flash"),
                    new AiModelOption(AiProvider.GOOGLE_GEMINI, "gemini-2.5-pro", "Gemini 2.5 Pro")
            )
    );

    public List<AiModelOption> all() {
        return models.values().stream().flatMap(List::stream).toList();
    }

    public List<AiModelOption> forProvider(AiProvider provider) {
        return models.getOrDefault(provider, List.of());
    }

    public void validate(AiProvider provider, String model) {
        boolean supported = forProvider(provider).stream()
                .anyMatch(option -> option.model().equals(model));

        if (!supported) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wybrany model nie pasuje do dostawcy AI.");
        }
    }
}
