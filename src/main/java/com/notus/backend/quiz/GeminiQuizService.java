package com.notus.backend.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notus.backend.quiz.dto.QuizResponse;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class GeminiQuizService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public QuizResponse generateQuiz(String text) {
        try {
            String shortenedText = text;
            if (shortenedText.length() > 6000) {
                shortenedText = shortenedText.substring(0, 6000);
            }

            String prompt = """
                    Na podstawie poniższego tekstu wygeneruj quiz w języku polskim.

                    Wymagania:
                    - wygeneruj dokładnie 5 pytań
                    - każde pytanie ma być pytaniem jednokrotnego wyboru
                    - każde pytanie ma mieć dokładnie 4 odpowiedzi
                    - tylko 1 odpowiedź ma być poprawna
                    - nie wymyślaj informacji spoza tekstu
                    - pytania mają dotyczyć treści dokumentu
                    - zwróć WYŁĄCZNIE czysty JSON, bez markdownu i bez komentarzy

                    Format JSON:
                    {
                      "title": "Quiz z dokumentu",
                      "questions": [
                        {
                          "question": "Treść pytania",
                          "options": ["A", "B", "C", "D"],
                          "correctAnswer": "Poprawna odpowiedź"
                        }
                      ]
                    }

                    TEKST:
                    """ + shortenedText;

            String body = """
                    {
                      "contents": [
                        {
                          "parts": [
                            {
                              "text": %s
                            }
                          ]
                        }
                      ]
                    }
                    """.formatted(objectMapper.writeValueAsString(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Gemini zwrócił błąd HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new RuntimeException("Brak odpowiedzi od Gemini: " + response.body());
            }

            String generatedText = candidates.get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

            String json = extractJson(generatedText);

            return objectMapper.readValue(json, QuizResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("Błąd przy generowaniu quizu przez Gemini.", e);
        }
    }

    private String extractJson(String generatedText) {
        int start = generatedText.indexOf("{");
        int end = generatedText.lastIndexOf("}");

        if (start == -1 || end == -1 || end <= start) {
            throw new RuntimeException("Nie udało się wyciągnąć JSON z odpowiedzi modelu: " + generatedText);
        }

        return generatedText.substring(start, end + 1);
    }
}
