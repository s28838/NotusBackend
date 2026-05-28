package com.notus.backend.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notus.backend.ai.AiModelCatalog;
import com.notus.backend.ai.AiProvider;
import com.notus.backend.quiz.dto.QuizResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class AiQuizGenerationService {

    private static final int MAX_SOURCE_CHARS = 6000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AiModelCatalog modelCatalog;

    public AiQuizGenerationService(AiModelCatalog modelCatalog) {
        this.modelCatalog = modelCatalog;
    }

    public QuizResponse generateQuiz(String text,
                                     AiProvider provider,
                                     String apiKey,
                                     String model,
                                     String title,
                                     String description,
                                     int questionCount) {
        modelCatalog.validate(provider, model);

        String resolvedTitle = hasText(title) ? title.trim() : "Quiz z dokumentu";
        String resolvedDescription = hasText(description) ? description.trim() : "";
        String prompt = buildPrompt(text, resolvedTitle, resolvedDescription, questionCount);
        String generatedText = switch (provider) {
            case OPENAI -> callOpenAi(apiKey, model, prompt, questionCount);
            case ANTHROPIC -> callAnthropic(apiKey, model, prompt, questionCount);
            case GOOGLE_GEMINI -> callGemini(apiKey, model, prompt);
        };

        try {
            QuizResponse quiz = objectMapper.readValue(extractJson(generatedText), QuizResponse.class);
            quiz.setTitle(resolvedTitle);
            quiz.setDescription(resolvedDescription);
            return quiz;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Model AI zwrócił odpowiedź w niepoprawnym formacie JSON.", ex);
        }
    }

    private String buildPrompt(String text, String title, String description, int questionCount) {
        String shortenedText = text.length() > MAX_SOURCE_CHARS ? text.substring(0, MAX_SOURCE_CHARS) : text;
        return String.format("""
                Na podstawie poniższego tekstu wygeneruj quiz w języku polskim.

                Wymagania:
                - tytuł quizu: "%s"
                - opis quizu: "%s"
                - wygeneruj dokładnie %d pytań
                - każde pytanie ma być pytaniem jednokrotnego wyboru
                - każde pytanie ma mieć dokładnie 4 odpowiedzi
                - tylko 1 odpowiedź ma być poprawna
                - nie wymyślaj informacji spoza tekstu
                - pytania mają dotyczyć treści dokumentu
                - zwróć WYŁĄCZNIE czysty JSON, bez markdownu i bez komentarzy

                Format JSON:
                {
                  "title": "%s",
                  "description": "%s",
                  "questions": [
                    {
                      "question": "Treść pytania",
                      "options": ["A", "B", "C", "D"],
                      "correctAnswer": "Poprawna odpowiedź"
                    }
                  ]
                }

                TEKST:
                %s
                """, title, description, questionCount, title, description, shortenedText);
    }

    private String callOpenAi(String apiKey, String model, String prompt, int questionCount) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", List.of(Map.of(
                                            "type", "input_text",
                                            "text", "Zwracaj wyłącznie poprawny JSON zgodny z poleceniem użytkownika."
                                    ))
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", List.of(Map.of(
                                            "type", "input_text",
                                            "text", prompt
                                    ))
                            )
                    ),
                    "text", Map.of("format", Map.of("type", "json_object")),
                    "max_output_tokens", outputTokenLimit(questionCount)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/responses"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            JsonNode root = sendJson(request, "OpenAI");
            String outputText = root.path("output_text").asText(null);
            if (outputText != null && !outputText.isBlank()) {
                return outputText;
            }
            return findText(root.path("output"));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie udało się wygenerować quizu przez OpenAI.", ex);
        }
    }

    private String callAnthropic(String apiKey, String model, String prompt, int questionCount) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", outputTokenLimit(questionCount),
                    "system", "Zwracaj wyłącznie poprawny JSON zgodny z poleceniem użytkownika.",
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", prompt
                    ))
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            JsonNode root = sendJson(request, "Anthropic");
            JsonNode content = root.path("content");
            if (content.isArray()) {
                for (JsonNode item : content) {
                    if ("text".equals(item.path("type").asText()) && item.hasNonNull("text")) {
                        return item.path("text").asText();
                    }
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Anthropic nie zwrócił treści odpowiedzi.");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie udało się wygenerować quizu przez Anthropic.", ex);
        }
    }

    private String callGemini(String apiKey, String model, String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt))
                    )),
                    "generationConfig", Map.of("responseMimeType", "application/json")
            );

            String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
            String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + encodedModel + ":generateContent?key=" + encodedKey))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            JsonNode root = sendJson(request, "Google Gemini");
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini nie zwrócił treści odpowiedzi.");
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                return parts.get(0).path("text").asText();
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini nie zwrócił treści odpowiedzi.");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie udało się wygenerować quizu przez Google Gemini.", ex);
        }
    }

    private JsonNode sendJson(HttpRequest request, String providerName) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    providerName + " zwrócił błąd HTTP " + response.statusCode() + ". Sprawdź klucz API, model i limity konta."
            );
        }
        return objectMapper.readTree(response.body());
    }

    private String findText(JsonNode node) {
        List<String> texts = new ArrayList<>();
        collectTextValues(node, texts);
        if (texts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI nie zwrócił treści odpowiedzi.");
        }
        return String.join("\n", texts);
    }

    private void collectTextValues(JsonNode node, List<String> texts) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            if ("output_text".equals(node.path("type").asText()) && node.hasNonNull("text")) {
                texts.add(node.path("text").asText());
                return;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                collectTextValues(fields.next().getValue(), texts);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectTextValues(child, texts);
            }
        }
    }

    private String extractJson(String generatedText) {
        int start = generatedText.indexOf("{");
        int end = generatedText.lastIndexOf("}");

        if (start == -1 || end == -1 || end <= start) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie udało się odczytać JSON z odpowiedzi modelu.");
        }

        return generatedText.substring(start, end + 1);
    }

    private int outputTokenLimit(int questionCount) {
        return Math.max(2000, Math.min(12000, questionCount * 650));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
