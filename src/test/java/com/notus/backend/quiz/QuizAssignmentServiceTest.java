package com.notus.backend.quiz;

import com.notus.backend.grades.GradeService;
import com.notus.backend.grades.QuizGradeCalculator;
import com.notus.backend.quiz.dto.AssignmentSummaryDto;
import com.notus.backend.quiz.dto.AssignQuizRequest;
import com.notus.backend.schedule.Schedule;
import com.notus.backend.schedule.ScheduleRepository;
import com.notus.backend.teachergroups.GroupMembershipRepository;
import com.notus.backend.teachergroups.TeacherGroupRepository;
import com.notus.backend.users.StudentRepository;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizAssignmentServiceTest {

    @Mock private QuizAssignmentRepository assignmentRepository;
    @Mock private QuizSubmissionRepository submissionRepository;
    @Mock private QuizAnswerRepository answerRepository;
    @Mock private QuizRepository quizRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private TeacherGroupRepository teacherGroupRepository;
    @Mock private GroupMembershipRepository groupMembershipRepository;
    @Mock private GradeService gradeService;
    @Mock private QuizGradeCalculator quizGradeCalculator;

    @InjectMocks private QuizAssignmentService service;

    @Test
    void assignQuiz_returnsSummaryDtoInsteadOfJpaEntity() {
        Teacher teacher = new Teacher();
        teacher.setId(7L);
        teacher.setClerkUserId("teacher_uid");

        Quiz quiz = new Quiz();
        quiz.setId(20L);
        quiz.setTeacher(teacher);
        quiz.setTitle("Quiz algebra");

        Schedule schedule = new Schedule();
        schedule.setId("lesson-1");
        schedule.setTeacherEntity(teacher);
        schedule.setSubject("Matematyka");
        schedule.setDate(Instant.parse("2026-05-26T10:00:00Z"));
        schedule.setTime("10:00 - 10:45");

        when(teacherRepository.findByClerkUserId("teacher_uid")).thenReturn(Optional.of(teacher));
        when(quizRepository.findById(20L)).thenReturn(Optional.of(quiz));
        when(scheduleRepository.findById("lesson-1")).thenReturn(Optional.of(schedule));
        when(assignmentRepository.existsByQuizIdAndScheduleId(20L, "lesson-1")).thenReturn(false);
        when(assignmentRepository.save(any(QuizAssignment.class))).thenAnswer(invocation -> {
            QuizAssignment assignment = invocation.getArgument(0);
            assignment.setId(100L);
            return assignment;
        });
        AssignmentSummaryDto result = service.assignQuiz("teacher_uid", new AssignQuizRequest(20L, "lesson-1"));

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.quizId()).isEqualTo(20L);
        assertThat(result.quizTitle()).isEqualTo("Quiz algebra");
        assertThat(result.scheduleId()).isEqualTo("lesson-1");
        assertThat(result.scheduleSubject()).isEqualTo("Matematyka");
        assertThat(result.submissionCount()).isZero();
    }
}
