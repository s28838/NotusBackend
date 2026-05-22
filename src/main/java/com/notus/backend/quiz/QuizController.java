package com.notus.backend.quiz;

import com.notus.backend.quiz.dto.QuizDetailsDto;
import com.notus.backend.quiz.dto.QuizResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/quiz")
@CrossOrigin(origins = "*")
public class QuizController {

    private static final Logger log = LoggerFactory.getLogger(QuizController.class);

    private final PdfQuizService pdfQuizService;
    private final QuizService quizService;

    public QuizController(PdfQuizService pdfQuizService, QuizService quizService) {
        this.pdfQuizService = pdfQuizService;
        this.quizService = quizService;
    }

    @PostMapping(value = "/from-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public QuizResponse generateQuizFromPdf(@RequestParam("file") MultipartFile file) {
        return pdfQuizService.generateQuizFromPdf(file);
    }

    @PostMapping("/save")
    public QuizDetailsDto saveQuiz(Principal principal, @RequestBody QuizResponse quizResponse) {
        String uid = principal.getName();
        return quizService.saveQuiz(uid, quizResponse);
    }

    @GetMapping("/my")
    public List<QuizDetailsDto> getMyQuizzes(Principal principal) {
        String uid = principal.getName();
        List<QuizDetailsDto> quizzes = quizService.getTeacherQuizzes(uid);
        log.debug("Fetching quizzes for UID: [{}], result size: {}", uid, quizzes != null ? quizzes.size() : "null");
        return quizzes;
    }

    @GetMapping("/{id}")
    public QuizDetailsDto getQuizDetails(Principal principal, @PathVariable Long id) {
        String uid = principal.getName();
        return quizService.getQuizDetails(uid, id);
    }

    @PutMapping("/{id}")
    public QuizDetailsDto updateQuiz(Principal principal,
                                     @PathVariable Long id,
                                     @RequestBody QuizResponse quizResponse) {
        String uid = principal.getName();
        return quizService.updateQuiz(uid, id, quizResponse);
    }

    @DeleteMapping("/{id}")
    public void deleteQuiz(Principal principal, @PathVariable Long id) {
        String uid = principal.getName();
        quizService.deleteQuiz(uid, id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadQuizPdf(Principal principal, @PathVariable Long id) throws IOException {
        String uid = principal.getName();
        QuizDetailsDto quiz = quizService.getQuizDetails(uid, id);

        byte[] pdfBytes = generateQuizPdf(quiz);

        String filename = "quiz-" + id + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }

    private byte[] generateQuizPdf(QuizDetailsDto quiz) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float margin = 50;
            float yStart = PDRectangle.A4.getHeight() - margin;
            float lineHeight = 16;
            float y = yStart;

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            cs.beginText();
            cs.setFont(fontBold, 18);
            cs.newLineAtOffset(margin, y);
            cs.showText(sanitize(quiz.title()));
            cs.endText();
            y -= lineHeight * 2;

            if (quiz.description() != null && !quiz.description().isBlank()) {
                cs.beginText();
                cs.setFont(fontRegular, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText(sanitize(quiz.description()));
                cs.endText();
                y -= lineHeight * 1.5f;
            }

            List<com.notus.backend.quiz.dto.QuizQuestionDto> questions = quiz.questions();
            for (int i = 0; i < questions.size(); i++) {
                com.notus.backend.quiz.dto.QuizQuestionDto q = questions.get(i);

                if (y < margin + lineHeight * 6) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = yStart;
                }

                cs.beginText();
                cs.setFont(fontBold, 12);
                cs.newLineAtOffset(margin, y);
                cs.showText((i + 1) + ". " + sanitize(q.questionText()));
                cs.endText();
                y -= lineHeight;

                if (q.options() != null && !q.options().isEmpty()) {
                    String[] letters = {"A", "B", "C", "D", "E"};
                    for (int j = 0; j < q.options().size(); j++) {
                        if (y < margin + lineHeight * 3) {
                            cs.close();
                            page = new PDPage(PDRectangle.A4);
                            doc.addPage(page);
                            cs = new PDPageContentStream(doc, page);
                            y = yStart;
                        }
                        String letter = j < letters.length ? letters[j] : String.valueOf((char) ('A' + j));
                        cs.beginText();
                        cs.setFont(fontRegular, 11);
                        cs.newLineAtOffset(margin + 16, y);
                        cs.showText(letter + ") " + sanitize(q.options().get(j)));
                        cs.endText();
                        y -= lineHeight;
                    }
                }

                if (q.correctAnswer() != null && !q.correctAnswer().isBlank()) {
                    cs.beginText();
                    cs.setFont(fontBold, 10);
                    cs.newLineAtOffset(margin + 16, y);
                    cs.showText("Odpowiedz: " + sanitize(q.correctAnswer()));
                    cs.endText();
                    y -= lineHeight;
                }

                y -= lineHeight * 0.5f;
            }

            cs.close();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\x00-\\x1F\\x7F]", "")
                   .replaceAll("[^\\x00-\\xFF]", "?");
    }
}
