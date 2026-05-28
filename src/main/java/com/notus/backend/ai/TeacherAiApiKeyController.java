package com.notus.backend.ai;

import com.notus.backend.ai.dto.CreateAiApiKeyRequest;
import com.notus.backend.ai.dto.TeacherAiApiKeyResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/teacher/ai-keys")
public class TeacherAiApiKeyController {

    private final TeacherAiApiKeyService apiKeyService;
    private final AiModelCatalog modelCatalog;

    public TeacherAiApiKeyController(TeacherAiApiKeyService apiKeyService,
                                     AiModelCatalog modelCatalog) {
        this.apiKeyService = apiKeyService;
        this.modelCatalog = modelCatalog;
    }

    @GetMapping
    public List<TeacherAiApiKeyResponse> list(Principal principal) {
        return apiKeyService.list(principal.getName());
    }

    @PostMapping
    public TeacherAiApiKeyResponse create(Principal principal,
                                          @Valid @RequestBody CreateAiApiKeyRequest request) {
        return apiKeyService.create(principal.getName(), request);
    }

    @DeleteMapping("/{id}")
    public void delete(Principal principal, @PathVariable Long id) {
        apiKeyService.delete(principal.getName(), id);
    }

    @GetMapping("/models")
    public List<AiModelOption> models() {
        return modelCatalog.all();
    }
}
