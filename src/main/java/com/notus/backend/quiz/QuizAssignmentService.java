package com.notus.backend.quiz;

import com.notus.backend.grades.GradeService;
import com.notus.backend.grades.QuizGradeCalculator;
import com.notus.backend.grades.dto.GradeResponse;
import com.notus.backend.quiz.dto.*;
import com.notus.backend.schedule.Schedule;
import com.notus.backend.schedule.ScheduleRepository;
import com.notus.backend.teachergroups.GroupMembershipRepository;
import com.notus.backend.teachergroups.GroupMembershipStatus;
import com.notus.backend.teachergroups.TeacherGroup;
import com.notus.backend.teachergroups.TeacherGroupRepository;
import com.notus.backend.users.Student;
import com.notus.backend.users.StudentRepository;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class QuizAssignmentService {

    private final QuizAssignmentRepository assignmentRepository;
    private final QuizSubmissionRepository submissionRepository;
    private final QuizAnswerRepository answerRepository;
    private final QuizRepository quizRepository;
    private final ScheduleRepository scheduleRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final TeacherGroupRepository teacherGroupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final GradeService gradeService;
    private final QuizGradeCalculator quizGradeCalculator;

    public QuizAssignmentService(
            QuizAssignmentRepository assignmentRepository,
            QuizSubmissionRepository submissionRepository,
            QuizAnswerRepository answerRepository,
            QuizRepository quizRepository,
            ScheduleRepository scheduleRepository,
            TeacherRepository teacherRepository,
            StudentRepository studentRepository,
            TeacherGroupRepository teacherGroupRepository,
            GroupMembershipRepository groupMembershipRepository,
            GradeService gradeService,
            QuizGradeCalculator quizGradeCalculator) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.answerRepository = answerRepository;
        this.quizRepository = quizRepository;
        this.scheduleRepository = scheduleRepository;
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
        this.teacherGroupRepository = teacherGroupRepository;
        this.groupMembershipRepository = groupMembershipRepository;
        this.gradeService = gradeService;
        this.quizGradeCalculator = quizGradeCalculator;
    }

    // --- Teacher: assign quiz to a schedule lesson ---

    @Transactional
    public QuizAssignment assignQuiz(String teacherClerkId, AssignQuizRequest req) {
        Teacher teacher = getTeacher(teacherClerkId);
        Quiz quiz = quizRepository.findById(req.quizId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz nie istnieje"));
        if (!quiz.getTeacher().equals(teacher)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień do tego quizu");
        }
        Schedule schedule = scheduleRepository.findById(req.scheduleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zajęcia nie istnieją"));

        // (Optional check) Ensure the schedule belongs to this teacher
        // if (schedule.getTeacher() != null && !schedule.getTeacher().equals(teacher)) { ... }

        if (assignmentRepository.existsByQuizIdAndScheduleId(req.quizId(), req.scheduleId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ten quiz jest już przypisany do tych zajęć");
        }

        QuizAssignment assignment = new QuizAssignment();
        assignment.setQuiz(quiz);
        assignment.setScheduleId(req.scheduleId());
        assignment.setTeacher(teacher);
        assignment.setAssignedAt(Instant.now());
        return assignmentRepository.save(assignment);
    }

    // --- Teacher: list all their assignments with stats ---

    @Transactional(readOnly = true)
    public List<AssignmentSummaryDto> getMyAssignments(String teacherClerkId) {
        Teacher teacher = getTeacher(teacherClerkId);
        List<QuizAssignment> assignments = assignmentRepository.findByTeacher(teacher);
        return assignments.stream().map(a -> buildSummary(a, null)).toList();
    }

    // --- Teacher: detailed results for one assignment ---

    @Transactional(readOnly = true)
    public AssignmentResultsDto getAssignmentResults(String teacherClerkId, Long assignmentId) {
        Teacher teacher = getTeacher(teacherClerkId);
        QuizAssignment assignment = getAssignment(assignmentId);
        if (!assignment.getTeacher().equals(teacher)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień");
        }

        Schedule schedule = scheduleRepository.findById(assignment.getScheduleId()).orElse(null);
        List<QuizSubmission> submissions = submissionRepository.findByAssignment(assignment);

        List<StudentScoreDto> scores = submissions.stream().map(s -> new StudentScoreDto(
                s.getStudent().getName(),
                s.getStudent().getIndexNumber(),
                s.getScore(),
                s.getTotal(),
                s.getSubmittedAt(),
                s.isPendingOpenReview(),
                s.getId()
        )).toList();

        return new AssignmentResultsDto(
                assignment.getId(),
                assignment.getQuiz().getTitle(),
                schedule != null ? schedule.getSubject() : "–",
                schedule != null ? formatDate(schedule.getDate()) : "–",
                schedule != null ? schedule.getTime() : "–",
                scores
        );
    }

    // --- Any user: check which schedule IDs have assignments (for badge display) ---

    @Transactional(readOnly = true)
    public List<ScheduleAssignmentDto> getAssignmentsForSchedules(List<String> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) return List.of();
        return assignmentRepository.findByScheduleIdIn(scheduleIds).stream()
                .map(a -> new ScheduleAssignmentDto(a.getId(), a.getScheduleId(), a.getQuiz().getTitle(), a.isActive()))
                .toList();
    }

    // --- Student: get assignment details to take quiz ---

    @Transactional(readOnly = true)
    public StudentAssignmentDto getStudentAssignment(String studentClerkId, Long assignmentId) {
        Student student = getStudent(studentClerkId);
        QuizAssignment assignment = getAssignment(assignmentId);
        Schedule schedule = scheduleRepository.findById(assignment.getScheduleId()).orElse(null);

        List<StudentQuestionDto> questions = assignment.getQuiz().getQuestions().stream()
                .map(q -> new StudentQuestionDto(q.getId(), q.getQuestionText(), q.getType(), q.getOptions()))
                .toList();

        QuizSubmission existing = submissionRepository
                .findByAssignmentAndStudent(assignment, student).orElse(null);

        return new StudentAssignmentDto(
                assignment.getId(),
                assignment.getQuiz().getTitle(),
                schedule != null ? schedule.getSubject() : "–",
                schedule != null ? formatDate(schedule.getDate()) : "–",
                schedule != null ? schedule.getTime() : "–",
                questions,
                existing != null,
                existing != null ? existing.getScore() : null,
                existing != null ? existing.getTotal() : null,
                existing != null && existing.isPendingOpenReview()
        );
    }

    // --- Student: submit answers ---

    @Transactional
    public SubmitResultDto submitAnswers(String studentClerkId, Long assignmentId, SubmitAnswersRequest req) {
        Student student = getStudent(studentClerkId);
        QuizAssignment assignment = getAssignment(assignmentId);

        List<QuizQuestion> questions = assignment.getQuiz().getQuestions();
        Map<Long, String> answers = req.answers() != null ? req.answers() : Map.of();

        int score = 0;
        boolean hasOpenQuestions = false;

        QuizSubmission submission = submissionRepository.findByAssignmentAndStudent(assignment, student)
                .orElseGet(QuizSubmission::new);
        boolean retake = submission.getId() != null;
        if (retake) {
            answerRepository.deleteAll(answerRepository.findBySubmission(submission));
        } else {
            submission.setAssignment(assignment);
            submission.setStudent(student);
        }
        submission.setSubmittedAt(Instant.now());
        submission.setTotal(questions.size());
        QuizSubmission saved = submissionRepository.save(submission);

        for (QuizQuestion q : questions) {
            String submitted = answers.getOrDefault(q.getId(), "");

            QuizAnswer qa = new QuizAnswer();
            qa.setSubmission(saved);
            qa.setQuestion(q);
            qa.setAnswerText(submitted);

            if (q.getType() == QuestionType.OPEN) {
                hasOpenQuestions = true;
                qa.setCorrect(null); // pending review
            } else {
                // CLOSED: auto-grade
                boolean correct = !submitted.isBlank()
                        && q.getCorrectAnswer() != null
                        && submitted.trim().equalsIgnoreCase(q.getCorrectAnswer().trim());
                qa.setCorrect(correct);
                if (correct) score++;
            }
            answerRepository.save(qa);
        }

        saved.setScore(score);
        saved.setPendingOpenReview(hasOpenQuestions);
        submissionRepository.save(saved);

        GradeResponse grade = hasOpenQuestions ? null : createGradeIfEnabled(assignment, student, score, questions.size());
        return new SubmitResultDto(score, questions.size(), percentage(score, questions.size()), grade != null, grade);
    }

    // --- Teacher: get open answers for review ---

    @Transactional(readOnly = true)
    public List<ReviewAnswerDto> getAnswersForReview(String teacherClerkId, Long submissionId) {
        Teacher teacher = getTeacher(teacherClerkId);
        QuizSubmission submission = getSubmission(submissionId);
        if (!submission.getAssignment().getTeacher().equals(teacher)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień");
        }
        return answerRepository.findBySubmission(submission).stream()
                .filter(a -> a.getQuestion().getType() == QuestionType.OPEN)
                .map(a -> new ReviewAnswerDto(
                        a.getId(),
                        a.getQuestion().getId(),
                        a.getQuestion().getQuestionText(),
                        a.getQuestion().getType(),
                        a.getAnswerText(),
                        a.getCorrect()
                ))
                .toList();
    }

    // --- Student: get full review of their answers ---
    @Transactional(readOnly = true)
    public MyQuizReviewDto getMyQuizReview(String studentClerkId, Long assignmentId) {
        Student student = getStudent(studentClerkId);
        QuizAssignment assignment = getAssignment(assignmentId);
        Schedule schedule = scheduleRepository.findById(assignment.getScheduleId()).orElse(null);

        QuizSubmission submission = submissionRepository
                .findByAssignmentAndStudent(assignment, student)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak zgłoszenia dla tego quizu"));

        List<QuizAnswer> answers = answerRepository.findBySubmission(submission);
        List<MyQuizReviewAnswerDto> dtos = answers.stream().map(a -> new MyQuizReviewAnswerDto(
                a.getQuestion().getId(),
                a.getQuestion().getQuestionText(),
                a.getQuestion().getType(),
                a.getQuestion().getOptions(),
                a.getQuestion().getCorrectAnswer(),
                a.getAnswerText(),
                a.getCorrect()
        )).toList();

        return new MyQuizReviewDto(
                assignment.getId(),
                assignment.getQuiz().getTitle(),
                schedule != null ? schedule.getSubject() : "–",
                submission.getScore(),
                submission.getTotal(),
                submission.isPendingOpenReview(),
                dtos
        );
    }

    // --- Teacher: submit review marks ---

    @Transactional
    public void reviewSubmission(String teacherClerkId, Long submissionId, ReviewSubmitRequest req) {
        Teacher teacher = getTeacher(teacherClerkId);
        QuizSubmission submission = getSubmission(submissionId);
        if (!submission.getAssignment().getTeacher().equals(teacher)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień");
        }

        List<QuizAnswer> answers = answerRepository.findBySubmission(submission);
        int openScore = 0;
        for (QuizAnswer a : answers) {
            if (a.getQuestion().getType() == QuestionType.OPEN) {
                Boolean mark = req.marks().get(a.getId());
                if (mark != null) {
                    a.setCorrect(mark);
                    answerRepository.save(a);
                    if (Boolean.TRUE.equals(mark)) openScore++;
                }
            }
        }

        // Add open question score to the existing closed score
        submission.setScore(submission.getScore() + openScore);
        submission.setPendingOpenReview(false);
        submission.setReviewedAt(Instant.now());
        submission.setNotificationSeen(false);
        submissionRepository.save(submission);
        createGradeIfEnabled(submission.getAssignment(), submission.getStudent(), submission.getScore(), submission.getTotal());
    }

    // --- Student: get new review notifications ---

    @Transactional(readOnly = true)
    public List<NewReviewNotificationDto> getNewReviews(String studentClerkId) {
        Student student = getStudent(studentClerkId);
        return submissionRepository
                .findByStudentAndReviewedAtIsNotNullAndNotificationSeenFalse(student)
                .stream()
                .map(s -> new NewReviewNotificationDto(
                        s.getId(),
                        s.getAssignment().getId(),
                        s.getAssignment().getQuiz().getTitle(),
                        s.getScore(),
                        s.getTotal()
                ))
                .toList();
    }

    // --- Student: mark notification as seen ---

    @Transactional
    public void markNotificationSeen(String studentClerkId, Long submissionId) {
        Student student = getStudent(studentClerkId);
        QuizSubmission submission = getSubmission(submissionId);
        if (!submission.getStudent().equals(student)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień");
        }
        submission.setNotificationSeen(true);
        submissionRepository.save(submission);
    }

    // --- Helpers ---

    private AssignmentSummaryDto buildSummary(QuizAssignment a, List<QuizSubmission> preloaded) {
        Schedule schedule = scheduleRepository.findById(a.getScheduleId()).orElse(null);
        List<QuizSubmission> submissions = preloaded != null ? preloaded : submissionRepository.findByAssignment(a);
        double avg = submissions.isEmpty() ? 0.0
                : submissions.stream().mapToDouble(s -> s.getTotal() == 0 ? 0 : (double) s.getScore() / s.getTotal() * 100).average().orElse(0.0);

        return new AssignmentSummaryDto(
                a.getId(),
                a.getQuiz().getId(),
                a.getQuiz().getTitle(),
                a.getScheduleId(),
                schedule != null ? schedule.getSubject() : "–",
                schedule != null ? formatDate(schedule.getDate()) : "–",
                schedule != null ? schedule.getTime() : "–",
                a.getAssignedAt(),
                submissions.size(),
                Math.round(avg * 10.0) / 10.0
        );
    }

    // --- Teacher: activate quiz for a live session ---

    @Transactional
    public void activateQuiz(String teacherClerkId, Long assignmentId, Long sessionId) {
        Teacher teacher = getTeacher(teacherClerkId);
        QuizAssignment assignment = getAssignment(assignmentId);
        if (!assignment.getTeacher().equals(teacher)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień");
        }
        assignment.setActive(true);
        assignment.setSessionId(sessionId);
        assignmentRepository.save(assignment);
    }

    @Transactional
    public void deactivateQuiz(String teacherClerkId, Long assignmentId) {
        Teacher teacher = getTeacher(teacherClerkId);
        QuizAssignment assignment = getAssignment(assignmentId);
        if (!assignment.getTeacher().equals(teacher)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień");
        }
        assignment.setActive(false);
        assignmentRepository.save(assignment);
    }

    // --- Student: get active quiz for their current session ---

    @Transactional(readOnly = true)
    public StudentAssignmentDto getActiveForSession(String studentClerkId, Long sessionId) {
        Student student = getStudent(studentClerkId);
        QuizAssignment assignment = assignmentRepository.findBySessionIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak aktywnego quizu"));
        Schedule schedule = scheduleRepository.findById(assignment.getScheduleId()).orElse(null);

        List<StudentQuestionDto> questions = assignment.getQuiz().getQuestions().stream()
                .map(q -> new StudentQuestionDto(q.getId(), q.getQuestionText(), q.getType(), q.getOptions()))
                .toList();

        QuizSubmission existing = submissionRepository
                .findByAssignmentAndStudent(assignment, student).orElse(null);

        return new StudentAssignmentDto(
                assignment.getId(),
                assignment.getQuiz().getTitle(),
                schedule != null ? schedule.getSubject() : "–",
                schedule != null ? formatDate(schedule.getDate()) : "–",
                schedule != null ? schedule.getTime() : "–",
                questions,
                existing != null,
                existing != null ? existing.getScore() : null,
                existing != null ? existing.getTotal() : null,
                existing != null && existing.isPendingOpenReview()
        );
    }

    private QuizAssignment getAssignment(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Przypisanie nie istnieje"));
    }

    private QuizSubmission getSubmission(Long id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zgłoszenie nie istnieje"));
    }

    private Teacher getTeacher(String clerkId) {
        return teacherRepository.findByClerkUserId(clerkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, 
                    "Nauczyciel nie istnieje lub brak uprawnień. Spróbuj odświeżyć stronę lub zalogować się ponownie."));
    }

    private Student getStudent(String clerkId) {
        return studentRepository.findByClerkUserId(clerkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student nie istnieje"));
    }

    private String formatDate(Instant instant) {
        if (instant == null) return "–";
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        return date.toString();
    }

    private GradeResponse createGradeIfEnabled(QuizAssignment assignment, Student student, int score, int total) {
        Quiz quiz = assignment.getQuiz();
        if (!quiz.isCountAsGrade()) {
            return null;
        }
        TeacherGroup group = resolveTeacherGroup(assignment, student);
        double percentage = percentage(score, total);
        String value = quizGradeCalculator.gradeForPercentage(percentage);
        return gradeService.createOrUpdateQuizGrade(
                group,
                student,
                quiz.getId(),
                value,
                quiz.getGradeWeight(),
                quiz.getGradeSemester(),
                "Quiz",
                quiz.getTitle(),
                "Ocena automatyczna z quizu"
        );
    }

    private TeacherGroup resolveTeacherGroup(QuizAssignment assignment, Student student) {
        Quiz quiz = assignment.getQuiz();
        if (quiz.getGroup() != null) {
            assertStudentInGroup(quiz.getGroup(), student);
            return quiz.getGroup();
        }

        Schedule schedule = scheduleRepository.findById(assignment.getScheduleId()).orElse(null);
        if (schedule != null && schedule.getSubject() != null) {
            List<TeacherGroup> groups = teacherGroupRepository.findByTeacherAndSubjectIgnoreCaseAndActiveTrue(assignment.getTeacher(), schedule.getSubject());
            return groups.stream()
                    .filter(group -> groupMembershipRepository.findByGroupAndStudentAndStatus(group, student, GroupMembershipStatus.ACTIVE).isPresent())
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Uczeń nie należy do grupy quizu."));
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quiz nie jest przypisany do grupy.");
    }

    private void assertStudentInGroup(TeacherGroup group, Student student) {
        if (groupMembershipRepository.findByGroupAndStudentAndStatus(group, student, GroupMembershipStatus.ACTIVE).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Uczeń nie należy do grupy quizu.");
        }
    }

    private double percentage(int score, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round((score * 1000.0) / total) / 10.0;
    }
}
