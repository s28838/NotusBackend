package com.notus.backend.teachergroups;

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
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String from;
    private final String fromName;
    private final BrevoEmailClient brevoEmailClient;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${spring.mail.host:}") String mailHost,
                        @Value("${notus.mail.from:no-reply@notus.local}") String from,
                        @Value("${notus.mail.from-name:Notus}") String fromName,
                        BrevoEmailClient brevoEmailClient) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
        this.from = from;
        this.fromName = fromName;
        this.brevoEmailClient = brevoEmailClient;
    }

    public void sendGroupInvitation(String email, String groupName, String teacherName, String inviteLink) {
        String textContent = buildTextContent(groupName, teacherName, inviteLink);
        String htmlContent = buildHtmlContent(groupName, teacherName, inviteLink);

        if (!brevoEmailClient.isConfigured()) {
            throw new IllegalStateException("Brevo API key is not configured for group invitations");
        }

        brevoEmailClient.sendEmail(
                email,
                from,
                fromName,
                "Zaproszenie do grupy " + groupName + " w Notus",
                textContent,
                htmlContent
        );
        log.info("Group invitation email sent to {} for group {} via Brevo API", email, groupName);
    }

    private void sendSmtp(String email, String groupName, String textContent, String htmlContent) {
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
            helper.setSubject("Zaproszenie do grupy " + groupName + " w Notus");
            helper.setText(textContent, htmlContent);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Could not build group invitation email", ex);
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException("Could not encode group invitation sender", ex);
        }

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new IllegalStateException("Could not send group invitation email via SMTP", ex);
        }
        log.info("Group invitation email sent to {} for group {}", email, groupName);
    }

    private String buildTextContent(String groupName, String teacherName, String inviteLink) {
        return """
                Hej!

                Zostałeś zaproszony do grupy %s przez nauczyciela %s.

                Kliknij link, aby zaakceptować zaproszenie:
                %s

                Link jest ważny przez 7 dni.

                Jeżeli nie spodziewałeś się tej wiadomości, możesz ją zignorować.
                """.formatted(groupName, teacherName, inviteLink);
    }

    private String buildHtmlContent(String groupName, String teacherName, String inviteLink) {
        return """
                <h2>Zaproszenie do grupy w Notus</h2>
                <p>Hej!</p>
                <p>
                  Zostałeś zaproszony do grupy <strong>%s</strong>
                  przez nauczyciela <strong>%s</strong>.
                </p>
                <p>Jeśli akceptujesz zaproszenie, kliknij poniższy przycisk:</p>
                <p>
                  <a href="%s"
                     style="display:inline-block;padding:12px 18px;background:#ff6b35;color:white;text-decoration:none;border-radius:8px;">
                    Dołącz do grupy
                  </a>
                </p>
                <p>Link jest ważny przez 7 dni.</p>
                <p>Jeżeli nie spodziewałeś się tej wiadomości, możesz ją zignorować.</p>
                """.formatted(escapeHtml(groupName), escapeHtml(teacherName), escapeHtml(inviteLink));
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
