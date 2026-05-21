package com.notus.backend.teachergroups;

import com.notus.backend.email.BrevoEmailClient;
import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    @Test
    void groupInvitationEmailIsNotusTransactionalEmailToRequestedStudent() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(mailSender);
        BrevoEmailClient brevoEmailClient = mock(BrevoEmailClient.class);
        when(brevoEmailClient.isConfigured()).thenReturn(false);

        EmailService service = new EmailService(provider, "smtp-relay.brevo.com", "no-reply@notus.app", "Notus", brevoEmailClient);

        service.sendGroupInvitation(
                "student@example.com",
                "Matematyka IA",
                "Jan Kowalski",
                "http://localhost:5173/invite/group?token=RAW_TOKEN"
        );

        verify(mailSender).send(message);
        assertEquals("Zaproszenie do grupy Matematyka IA w Notus", message.getSubject());
        assertEquals("student@example.com", message.getAllRecipients()[0].toString());
        assertEquals("Notus <no-reply@notus.app>", message.getFrom()[0].toString());

        String content = extractText(message.getContent());
        assertTrue(content.contains("Matematyka IA"));
        assertTrue(content.contains("Jan Kowalski"));
        assertTrue(content.contains("/invite/group?token=RAW_TOKEN"));
        assertFalse(content.contains("Brevo account"));
        assertFalse(content.contains("Validate your account"));
        assertFalse(content.contains("smtp-brevo.com"));
    }

    private String extractText(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }

        if (content instanceof Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                builder.append(extractText(multipart.getBodyPart(i).getContent()));
            }
            return builder.toString();
        }

        return "";
    }
}
