package com.notus.backend.teachergroups.dto;

import com.notus.backend.teachergroups.GroupInvitationStatus;

import java.time.Instant;

public record GroupInvitationResponse(
        Long id,
        Long groupId,
        String groupName,
        String email,
        String invitationLink,
        GroupInvitationStatus status,
        Instant createdAt,
        Instant lastSentAt,
        Instant resendAvailableAt,
        Instant expiresAt,
        Instant acceptedAt,
        String message
) {}
