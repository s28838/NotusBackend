package com.notus.backend.ai;

import com.notus.backend.users.Teacher;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "teacher_ai_api_keys",
        indexes = {
                @Index(name = "idx_teacher_ai_keys_teacher", columnList = "teacher_id"),
                @Index(name = "idx_teacher_ai_keys_provider", columnList = "provider")
        })
@Getter
@Setter
@NoArgsConstructor
public class TeacherAiApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AiProvider provider;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "encrypted_api_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedApiKey;

    @Column(name = "key_preview", nullable = false, length = 32)
    private String keyPreview;

    @Column(nullable = false, length = 128)
    private String fingerprint;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
