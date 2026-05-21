package com.notus.backend.teachergroups;

import com.notus.backend.users.Student;
import com.notus.backend.users.Teacher;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "group_invitations",
        indexes = {
                @Index(name = "idx_group_invitations_token_hash", columnList = "token_hash"),
                @Index(name = "idx_group_invitations_email", columnList = "email")
        })
@Getter
@Setter
@NoArgsConstructor
public class GroupInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private TeacherGroup group;

    private String email;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "invitation_link", columnDefinition = "TEXT")
    private String invitationLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupInvitationStatus status = GroupInvitationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    @Column(name = "resend_count")
    private Integer resendCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by_user_id")
    private Student acceptedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_teacher_id", nullable = false)
    private Teacher createdByTeacher;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
