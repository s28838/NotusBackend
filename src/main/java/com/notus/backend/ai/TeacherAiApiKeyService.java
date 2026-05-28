package com.notus.backend.ai;

import com.notus.backend.ai.dto.CreateAiApiKeyRequest;
import com.notus.backend.ai.dto.TeacherAiApiKeyResponse;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TeacherAiApiKeyService {

    private final TeacherRepository teacherRepository;
    private final TeacherAiApiKeyRepository apiKeyRepository;
    private final SecretEncryptionService encryptionService;

    public TeacherAiApiKeyService(TeacherRepository teacherRepository,
                                  TeacherAiApiKeyRepository apiKeyRepository,
                                  SecretEncryptionService encryptionService) {
        this.teacherRepository = teacherRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.encryptionService = encryptionService;
    }

    @Transactional(readOnly = true)
    public List<TeacherAiApiKeyResponse> list(String teacherUid) {
        Teacher teacher = requireTeacher(teacherUid);
        return apiKeyRepository.findByTeacherAndActiveTrueOrderByCreatedAtDesc(teacher)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TeacherAiApiKeyResponse create(String teacherUid, CreateAiApiKeyRequest request) {
        Teacher teacher = requireTeacher(teacherUid);
        String apiKey = request.apiKey().trim();
        String fingerprint = encryptionService.fingerprint(apiKey);

        if (apiKeyRepository.existsByTeacherAndProviderAndFingerprintAndActiveTrue(teacher, request.provider(), fingerprint)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ten klucz API jest już zapisany dla wybranego dostawcy.");
        }

        TeacherAiApiKey entity = new TeacherAiApiKey();
        entity.setTeacher(teacher);
        entity.setProvider(request.provider());
        entity.setLabel(resolveLabel(request.label(), request.provider()));
        entity.setEncryptedApiKey(encryptionService.encrypt(apiKey));
        entity.setFingerprint(fingerprint);
        entity.setKeyPreview(mask(apiKey));

        return toResponse(apiKeyRepository.save(entity));
    }

    @Transactional
    public void delete(String teacherUid, Long id) {
        TeacherAiApiKey key = requireKey(teacherUid, id);
        key.setActive(false);
        apiKeyRepository.save(key);
    }

    @Transactional(readOnly = true)
    public ResolvedAiApiKey resolve(String teacherUid, Long id) {
        TeacherAiApiKey key = requireKey(teacherUid, id);
        return new ResolvedAiApiKey(key.getProvider(), encryptionService.decrypt(key.getEncryptedApiKey()));
    }

    private TeacherAiApiKey requireKey(String teacherUid, Long id) {
        Teacher teacher = requireTeacher(teacherUid);
        return apiKeyRepository.findByIdAndTeacherAndActiveTrue(id, teacher)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono klucza API."));
    }

    private Teacher requireTeacher(String teacherUid) {
        return teacherRepository.findByClerkUserId(teacherUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono konta nauczyciela."));
    }

    private TeacherAiApiKeyResponse toResponse(TeacherAiApiKey key) {
        return new TeacherAiApiKeyResponse(
                key.getId(),
                key.getProvider(),
                key.getLabel(),
                key.getKeyPreview(),
                key.getCreatedAt()
        );
    }

    private String resolveLabel(String label, AiProvider provider) {
        if (label != null && !label.trim().isBlank()) {
            return label.trim();
        }
        return switch (provider) {
            case OPENAI -> "OpenAI";
            case ANTHROPIC -> "Anthropic";
            case GOOGLE_GEMINI -> "Google Gemini";
        };
    }

    private String mask(String apiKey) {
        String suffix = apiKey.length() <= 4 ? apiKey : apiKey.substring(apiKey.length() - 4);
        return "****" + suffix;
    }

    public record ResolvedAiApiKey(AiProvider provider, String apiKey) {}
}
