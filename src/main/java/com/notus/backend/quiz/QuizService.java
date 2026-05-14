package com.notus.backend.quiz;

import com.notus.backend.quiz.dto.QuestionDto;
import com.notus.backend.quiz.dto.QuizDetailsDto;
import com.notus.backend.quiz.dto.QuizResponse;
import com.notus.backend.teachergroups.TeacherGroup;
import com.notus.backend.teachergroups.TeacherGroupRepository;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final TeacherRepository teacherRepository;
    private final TeacherGroupRepository teacherGroupRepository;
    private final QuizAssignmentRepository quizAssignmentRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;

    public QuizService(QuizRepository quizRepository,
                       TeacherRepository teacherRepository,
                       TeacherGroupRepository teacherGroupRepository,
                       QuizAssignmentRepository quizAssignmentRepository,
                       QuizSubmissionRepository quizSubmissionRepository) {
        this.quizRepository = quizRepository;
        this.teacherRepository = teacherRepository;
        this.teacherGroupRepository = teacherGroupRepository;
        this.quizAssignmentRepository = quizAssignmentRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Teacher getTeacherByClerkId(String clerkUserId) {
        return teacherRepository.findByClerkUserId(clerkUserId)
                .orElseThrow(() -> new IllegalArgumentException("Nauczyciel nie istnieje: " + clerkUserId));
    }

    private Quiz getQuizEntity(String clerkUserId, Long quizId) {
        Teacher teacher = getTeacherByClerkId(clerkUserId);
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz nie istnieje"));
        if (!quiz.getTeacher().equals(teacher)) {
            throw new IllegalArgumentException("Brak uprawnień do tego quizu");
        }
        return quiz;
    }

    private QuizDetailsDto toDetailsDto(Quiz quiz, boolean hasSubmissions) {
        return new QuizDetailsDto(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getCreatedAt(),
                quiz.getVersion(),
                quiz.getGroup() != null ? quiz.getGroup().getId() : null,
                quiz.isCountAsGrade(),
                quiz.getGradeWeight(),
                quiz.getGradeSemester(),
                quiz.getQuestions(),
                hasSubmissions
        );
    }

    private void applyQuestionsFromDto(Quiz quiz, List<QuestionDto> questionDtos) {
        quiz.getQuestions().clear();
        if (questionDtos == null) return;
        for (QuestionDto qDto : questionDtos) {
            QuizQuestion q = new QuizQuestion();
            q.setQuestionText(qDto.getQuestion());
            q.setType(qDto.getType() != null ? qDto.getType() : QuestionType.CLOSED);
            q.setOptions(qDto.getOptions() != null ? qDto.getOptions() : new ArrayList<>());
            q.setCorrectAnswer(qDto.getCorrectAnswer());
            quiz.addQuestion(q);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional
    public Quiz saveQuiz(String clerkUserId, QuizResponse dto) {
        Teacher teacher = getTeacherByClerkId(clerkUserId);
        Quiz quiz = new Quiz();
        quiz.setTeacher(teacher);
        quiz.setTitle(dto.getTitle());
        quiz.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        quiz.setCreatedAt(Instant.now());
        applyGradeSettings(quiz, teacher, dto);
        applyQuestionsFromDto(quiz, dto.getQuestions());
        return quizRepository.save(quiz);
    }

    @Transactional(readOnly = true)
    public List<Quiz> getTeacherQuizzes(String clerkUserId) {
        Teacher teacher = getTeacherByClerkId(clerkUserId);
        return quizRepository.findByTeacherAndArchivedFalse(teacher);
    }

    @Transactional(readOnly = true)
    public QuizDetailsDto getQuizDetails(String clerkUserId, Long quizId) {
        Quiz quiz = getQuizEntity(clerkUserId, quizId);
        List<QuizAssignment> assignments = quizAssignmentRepository.findByQuiz(quiz);
        boolean hasSubmissions = assignments.stream()
                .anyMatch(a -> quizSubmissionRepository.existsByAssignment(a));
        return toDetailsDto(quiz, hasSubmissions);
    }

    @Transactional
    public void deleteQuiz(String clerkUserId, Long quizId) {
        Quiz quiz = getQuizEntity(clerkUserId, quizId);
        quizRepository.delete(quiz);
    }

    @Transactional
    public QuizDetailsDto updateQuiz(String clerkUserId, Long quizId, QuizResponse dto) {
        Quiz quiz = getQuizEntity(clerkUserId, quizId);
        List<QuizAssignment> assignments = quizAssignmentRepository.findByQuiz(quiz);

        boolean anySubmissions = assignments.stream()
                .anyMatch(a -> quizSubmissionRepository.existsByAssignment(a));

        if (!anySubmissions) {
            // Safe to update in-place — no student has answered yet
            quiz.setTitle(dto.getTitle());
            if (dto.getDescription() != null) quiz.setDescription(dto.getDescription());
            applyGradeSettings(quiz, quiz.getTeacher(), dto);
            applyQuestionsFromDto(quiz, dto.getQuestions());
            Quiz saved = quizRepository.save(quiz);
            return toDetailsDto(saved, false);
        }

        // Fork: create a new version
        Quiz newQuiz = new Quiz();
        newQuiz.setTeacher(quiz.getTeacher());
        newQuiz.setTitle(dto.getTitle());
        newQuiz.setDescription(dto.getDescription() != null ? dto.getDescription() : quiz.getDescription());
        newQuiz.setCreatedAt(Instant.now());
        newQuiz.setVersion(quiz.getVersion() + 1);
        newQuiz.setParentQuizId(quiz.getId());
        applyGradeSettings(newQuiz, quiz.getTeacher(), dto);
        applyQuestionsFromDto(newQuiz, dto.getQuestions());
        Quiz savedNew = quizRepository.save(newQuiz);

        // Re-point assignments that have no submissions to the new version
        for (QuizAssignment assignment : assignments) {
            if (!quizSubmissionRepository.existsByAssignment(assignment)) {
                assignment.setQuiz(savedNew);
                quizAssignmentRepository.save(assignment);
            }
        }

        // Archive the old quiz
        quiz.setArchived(true);
        quizRepository.save(quiz);

        return toDetailsDto(savedNew, false);
    }

    private void applyGradeSettings(Quiz quiz, Teacher teacher, QuizResponse dto) {
        if (dto.getGroupId() != null) {
            TeacherGroup group = teacherGroupRepository.findByIdAndTeacherAndActiveTrue(dto.getGroupId(), teacher)
                    .orElseThrow(() -> new IllegalArgumentException("Grupa nie istnieje albo nie należy do nauczyciela"));
            quiz.setGroup(group);
        }
        boolean countAsGrade = Boolean.TRUE.equals(dto.getCountAsGrade());
        quiz.setCountAsGrade(countAsGrade);
        if (countAsGrade) {
            if (dto.getGradeWeight() == null || dto.getGradeWeight() <= 0) {
                throw new IllegalArgumentException("Waga oceny z quizu musi być większa od 0");
            }
            if (dto.getSemester() == null || dto.getSemester().isBlank()) {
                throw new IllegalArgumentException("Semestr oceny z quizu jest wymagany");
            }
            quiz.setGradeWeight(dto.getGradeWeight());
            quiz.setGradeSemester(dto.getSemester().trim());
        } else {
            quiz.setGradeWeight(null);
            quiz.setGradeSemester(null);
        }
    }
}
