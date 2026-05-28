package com.notus.backend.quiz;

import com.notus.backend.ai.TeacherAiApiKeyService;
import com.notus.backend.ai.TeacherAiApiKeyService.ResolvedAiApiKey;
import com.notus.backend.quiz.dto.QuizResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PdfQuizService {

    private final PdfTextExtractorService pdfTextExtractorService;
    private final TeacherAiApiKeyService apiKeyService;
    private final AiQuizGenerationService aiQuizGenerationService;

    public PdfQuizService(PdfTextExtractorService pdfTextExtractorService,
                          TeacherAiApiKeyService apiKeyService,
                          AiQuizGenerationService aiQuizGenerationService) {
        this.pdfTextExtractorService = pdfTextExtractorService;
        this.apiKeyService = apiKeyService;
        this.aiQuizGenerationService = aiQuizGenerationService;
    }

    public QuizResponse generateQuizFromPdf(String teacherUid,
                                            MultipartFile file,
                                            Long apiKeyId,
                                            String model,
                                            String title,
                                            String description,
                                            Integer questionCount) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie wybrano pliku.");
        }
        if (apiKeyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wybierz klucz API do generowania quizu.");
        }
        if (model == null || model.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wybierz model AI do generowania quizu.");
        }

        int resolvedQuestionCount = questionCount != null ? questionCount : 5;
        if (resolvedQuestionCount < 1 || resolvedQuestionCount > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Liczba pytań musi być od 1 do 30.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase("application/pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dozwolony jest tylko plik PDF.");
        }

        String text = pdfTextExtractorService.extractText(file);

        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie udało się wyciągnąć tekstu z PDF.");
        }

        ResolvedAiApiKey resolvedKey = apiKeyService.resolve(teacherUid, apiKeyId);
        return aiQuizGenerationService.generateQuiz(
                text,
                resolvedKey.provider(),
                resolvedKey.apiKey(),
                model.trim(),
                title,
                description,
                resolvedQuestionCount
        );
    }
}
