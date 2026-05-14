package com.notus.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private final String frontendBaseUrl;

    public EmailVerificationService(@Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public void sendVerificationEmail(String email, String verificationToken) {
        log.info("Teacher email verification for {}: {}/verify-email?token={}", email, frontendBaseUrl, verificationToken);
    }
}
