package com.notus.backend.ai;

import com.notus.backend.users.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherAiApiKeyRepository extends JpaRepository<TeacherAiApiKey, Long> {
    List<TeacherAiApiKey> findByTeacherAndActiveTrueOrderByCreatedAtDesc(Teacher teacher);
    Optional<TeacherAiApiKey> findByIdAndTeacherAndActiveTrue(Long id, Teacher teacher);
    boolean existsByTeacherAndProviderAndFingerprintAndActiveTrue(Teacher teacher, AiProvider provider, String fingerprint);
}
