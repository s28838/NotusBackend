package com.notus.backend.auth;

import com.notus.backend.email.BrevoEmailClient;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String from;
    private final String fromName;
    private final String frontendBaseUrl;
    private final BrevoEmailClient brevoEmailClient;

    public EmailVerificationService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                    @Value("${spring.mail.host:}") String mailHost,
                                    @Value("${notus.mail.from:no-reply@notus.local}") String from,
                                    @Value("${notus.mail.from-name:Notus}") String fromName,
                                    @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl,
                                    BrevoEmailClient brevoEmailClient) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
        this.from = from;
        this.fromName = fromName;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.brevoEmailClient = brevoEmailClient;
    }

    public void sendVerificationEmail(String email, String verificationToken) {
        String verificationLink = "%s/verify-email?token=%s".formatted(frontendBaseUrl, verificationToken);
        String textContent = buildTextContent(verificationLink);
        String htmlContent = buildHtmlContent(verificationLink);

        if (brevoEmailClient.isConfigured()) {
            brevoEmailClient.sendEmail(
                    email,
                    from,
                    fromName,
                    "Potwierdź email w Notus",
                    textContent,
                    htmlContent
            );
            log.info("Teacher email verification sent to {} via Brevo API", email);
            return;
        }

        if (isSmtpConfigured()) {
            sendSmtp(email, textContent, htmlContent);
            return;
        }

        log.info("Teacher email verification for {}: {}", email, verificationLink);
    }

    private void sendSmtp(String email, String textContent, String htmlContent) {
        JavaMailSender mailSender = mailSenderProvider.getObject();
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(from, fromName);
            helper.setTo(email);
            helper.setSubject("Potwierdź email w Notus");
            helper.setText(textContent, htmlContent);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Could not build verification email", ex);
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException("Could not encode verification email sender", ex);
        }

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new IllegalStateException("Could not send verification email via SMTP", ex);
        }
        log.info("Teacher email verification sent to {}", email);
    }

    private String buildTextContent(String verificationLink) {
        return """
                Hej!

                Potwierdź adres email, aby aktywować konto nauczyciela w Notus:
                %s

                Link jest ważny przez 24 godziny.

                Jeżeli nie zakładałeś konta w Notus, możesz zignorować tę wiadomość.
                """.formatted(verificationLink);
    }

    private String buildHtmlContent(String verificationLink) {
        String escapedLink = escapeHtml(verificationLink);
        return """
                <h2>Potwierdź email w Notus</h2>
                <p>Hej!</p>
                <p>Potwierdź adres email, aby aktywować konto nauczyciela w Notus.</p>
                <p>
                  <a href="%s"
                     style="display:inline-block;padding:12px 18px;background:#ff6b35;color:white;text-decoration:none;border-radius:8px;">
                    Potwierdź email
                  </a>
                </p>
                <p>Link jest ważny przez 24 godziny.</p>
                <p>Jeżeli nie zakładałeś konta w Notus, możesz zignorować tę wiadomość.</p>
                """.formatted(escapedLink);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private boolean isSmtpConfigured() {
        return mailHost != null && !mailHost.isBlank();
    }
}
