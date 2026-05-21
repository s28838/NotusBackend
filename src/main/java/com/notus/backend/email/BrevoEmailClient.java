package com.notus.backend.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class BrevoEmailClient {

    private final RestClient restClient;
    private final String apiKey;

    public BrevoEmailClient(@Value("${brevo.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.restClient = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public void sendEmail(String toEmail,
                          String fromEmail,
                          String fromName,
                          String subject,
                          String textContent,
                          String htmlContent) {
        Map<String, Object> request = Map.of(
                "sender", Map.of("name", fromName, "email", fromEmail),
                "to", List.of(Map.of("email", toEmail)),
                "subject", subject,
                "htmlContent", htmlContent,
                "textContent", textContent
        );

        try {
            restClient.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new IllegalStateException("Could not send email via Brevo API", ex);
        }
    }
}
