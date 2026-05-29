package com.notus.backend.teachergroups;

import com.notus.backend.email.BrevoEmailClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    @Test
    void groupInvitationEmailIsSentViaBrevoApi() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        BrevoEmailClient brevoEmailClient = mock(BrevoEmailClient.class);
        when(brevoEmailClient.isConfigured()).thenReturn(true);

        EmailService service = new EmailService(provider, "", "no-reply@notus.app", "Notus", brevoEmailClient);

        service.sendGroupInvitation(
                "student@example.com",
                "Matematyka IA",
                "Jan Kowalski",
                "http://localhost:5173/invite/group?token=RAW_TOKEN"
        );

        verify(brevoEmailClient).sendEmail(
                eq("student@example.com"),
                eq("no-reply@notus.app"),
                eq("Notus"),
                eq("Zaproszenie do grupy Matematyka IA w Notus"),
                contains("Matematyka IA"),
                contains("/invite/group?token=RAW_TOKEN")
        );
        verify(provider, never()).getObject();
    }

    @Test
    void groupInvitationFailsWhenBrevoApiIsNotConfigured() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        BrevoEmailClient brevoEmailClient = mock(BrevoEmailClient.class);
        when(brevoEmailClient.isConfigured()).thenReturn(false);

        EmailService service = new EmailService(provider, "smtp-relay.brevo.com", "no-reply@notus.app", "Notus", brevoEmailClient);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.sendGroupInvitation(
                "student@example.com",
                "Matematyka IA",
                "Jan Kowalski",
                "http://localhost:5173/invite/group?token=RAW_TOKEN"
        ));
        assertTrue(ex.getMessage().contains("Brevo API key is not configured"));
        verify(provider, never()).getObject();
    }
}
