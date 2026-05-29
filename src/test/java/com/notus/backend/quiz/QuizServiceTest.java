package com.notus.backend.quiz;

import com.notus.backend.quiz.dto.QuizDetailsDto;
import com.notus.backend.quiz.dto.QuestionDto;
import com.notus.backend.quiz.dto.QuizResponse;
import com.notus.backend.users.Role;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock private QuizRepository quizRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private QuizAssignmentRepository quizAssignmentRepository;
    @Mock private QuizSubmissionRepository quizSubmissionRepository;

    @InjectMocks private QuizService quizService;

    private Teacher teacher;
    private Quiz quiz;

    @BeforeEach
    void setUp() {
        teacher = new Teacher();
        teacher.setId(1L);
        teacher.setClerkUserId("clerk_123");
        teacher.setEmail("teacher@test.com");
        teacher.setName("Test Teacher");
        teacher.setRole(Role.TEACHER);

        quiz = new Quiz();
        quiz.setId(10L);
        quiz.setTeacher(teacher);
        quiz.setTitle("Original Quiz");
        quiz.setDescription("");
        quiz.setVersion(1);
        quiz.setArchived(false);
    }

    @Test
    void saveQuiz_closedQuestionWithoutCorrectAnswerIsRejected() {
        QuestionDto question = new QuestionDto();
        question.setType(QuestionType.CLOSED);
        question.setQuestion("Która odpowiedź jest poprawna?");
        question.setOptions(List.of("A", "B", "C", "D"));
        question.setCorrectAnswer("");

        QuizResponse dto = new QuizResponse("Quiz", "", List.of(question));
        when(teacherRepository.findByClerkUserId("clerk_123")).thenReturn(Optional.of(teacher));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> quizService.saveQuiz("clerk_123", dto)
        );

        assertTrue(ex.getReason().contains("Zaznacz poprawną odpowiedź"));
        verify(quizRepository, never()).save(any());
    }

    @Test
    void saveQuiz_closedQuestionCorrectAnswerMustMatchOption() {
        QuestionDto question = new QuestionDto();
        question.setType(QuestionType.CLOSED);
        question.setQuestion("Która odpowiedź jest poprawna?");
        question.setOptions(List.of("A", "B", "C", "D"));
        question.setCorrectAnswer("E");

        QuizResponse dto = new QuizResponse("Quiz", "", List.of(question));
        when(teacherRepository.findByClerkUserId("clerk_123")).thenReturn(Optional.of(teacher));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> quizService.saveQuiz("clerk_123", dto)
        );

        assertTrue(ex.getReason().contains("musi być jedną z wpisanych opcji"));
        verify(quizRepository, never()).save(any());
    }

    @Test
    void updateQuiz_noSubmissions_updatesInPlace() {
        QuizResponse dto = new QuizResponse("Updated Title", "", List.of());

        when(teacherRepository.findByClerkUserId("clerk_123")).thenReturn(Optional.of(teacher));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(quizAssignmentRepository.findByQuiz(quiz)).thenReturn(List.of());
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizDetailsDto result = quizService.updateQuiz("clerk_123", 10L, dto);

        assertEquals(10L, result.id());
        assertEquals("Updated Title", result.title());
        assertEquals(1, result.version()); // version unchanged
        assertFalse(quiz.isArchived());
        verify(quizRepository, times(1)).save(quiz);
    }

    @Test
    void updateQuiz_withSubmissions_forksAndReassignsOpenAssignments() {
        QuizResponse dto = new QuizResponse("Updated Title", "", List.of());

        QuizAssignment assignmentWithSub = new QuizAssignment();
        assignmentWithSub.setId(100L);
        assignmentWithSub.setQuiz(quiz);

        QuizAssignment assignmentWithoutSub = new QuizAssignment();
        assignmentWithoutSub.setId(101L);
        assignmentWithoutSub.setQuiz(quiz);

        when(teacherRepository.findByClerkUserId("clerk_123")).thenReturn(Optional.of(teacher));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(quizAssignmentRepository.findByQuiz(quiz))
                .thenReturn(List.of(assignmentWithSub, assignmentWithoutSub));
        when(quizSubmissionRepository.existsByAssignment(assignmentWithSub)).thenReturn(true);
        when(quizSubmissionRepository.existsByAssignment(assignmentWithoutSub)).thenReturn(false);

        // Give the new quiz an id when saved for the first time (id==null)
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> {
            Quiz q = inv.getArgument(0);
            if (q.getId() == null) q.setId(11L);
            return q;
        });

        QuizDetailsDto result = quizService.updateQuiz("clerk_123", 10L, dto);

        // New version returned
        assertEquals(11L, result.id());
        assertEquals(2, result.version());

        // Old quiz archived
        assertTrue(quiz.isArchived());

        // Assignment without submissions re-pointed to new quiz
        assertEquals(11L, assignmentWithoutSub.getQuiz().getId());
        verify(quizAssignmentRepository).save(assignmentWithoutSub);

        // Assignment with submissions left on old quiz
        assertEquals(10L, assignmentWithSub.getQuiz().getId());
        verify(quizAssignmentRepository, never()).save(assignmentWithSub);
    }

    @Test
    void getQuizDetails_noSubmissions_hasSubmissionsFalse() {
        when(teacherRepository.findByClerkUserId("clerk_123")).thenReturn(Optional.of(teacher));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(quizAssignmentRepository.findByQuiz(quiz)).thenReturn(List.of());

        QuizDetailsDto result = quizService.getQuizDetails("clerk_123", 10L);

        assertFalse(result.hasSubmissions());
    }

    @Test
    void getQuizDetails_withSubmissions_hasSubmissionsTrue() {
        QuizAssignment assignment = new QuizAssignment();
        assignment.setId(100L);
        assignment.setQuiz(quiz);

        when(teacherRepository.findByClerkUserId("clerk_123")).thenReturn(Optional.of(teacher));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(quizAssignmentRepository.findByQuiz(quiz)).thenReturn(List.of(assignment));
        when(quizSubmissionRepository.existsByAssignment(assignment)).thenReturn(true);

        QuizDetailsDto result = quizService.getQuizDetails("clerk_123", 10L);

        assertTrue(result.hasSubmissions());
    }
}
