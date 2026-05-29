package com.notus.backend.quiz;

import com.notus.backend.quiz.dto.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quiz-assignments")
@CrossOrigin(origins = "*")
public class QuizAssignmentController {

    private final QuizAssignmentService service;

    public QuizAssignmentController(QuizAssignmentService service) {
        this.service = service;
    }

    /** Teacher: assign a quiz to a schedule lesson */
    @PostMapping
    public AssignmentSummaryDto assignQuiz(Principal principal, @RequestBody AssignQuizRequest req) {
        return service.assignQuiz(principal.getName(), req);
    }

    /** Teacher: get all their assignments with submission stats */
    @GetMapping("/my")
    public List<AssignmentSummaryDto> getMyAssignments(Principal principal) {
        return service.getMyAssignments(principal.getName());
    }

    /** Teacher: get detailed submission results for one assignment */
    @GetMapping("/{id}/results")
    public AssignmentResultsDto getResults(Principal principal, @PathVariable Long id) {
        return service.getAssignmentResults(principal.getName(), id);
    }

    /** Teacher: get detailed submission results for the active quiz in a live session */
    @GetMapping("/session/{sessionId}/results")
    public AssignmentResultsDto getSessionResults(Principal principal, @PathVariable Long sessionId) {
        return service.getSessionAssignmentResults(principal.getName(), sessionId);
    }

    /** Any authenticated user: check which scheduleIds have quiz assignments (for badge display) */
    @GetMapping("/by-schedules")
    public List<ScheduleAssignmentDto> getBySchedules(@RequestParam List<String> scheduleIds) {
        return service.getAssignmentsForSchedules(scheduleIds);
    }

    /** Student: get assignment to take quiz (no correct answers) */
    @GetMapping("/{id}/take")
    public StudentAssignmentDto getForStudent(Principal principal, @PathVariable Long id) {
        return service.getStudentAssignment(principal.getName(), id);
    }

    /** Student: get complete review of their own submitted answers */
    @GetMapping("/{id}/my-answers")
    public MyQuizReviewDto getMyQuizReview(Principal principal, @PathVariable Long id) {
        return service.getMyQuizReview(principal.getName(), id);
    }

    /** Student: submit quiz answers */
    @PostMapping("/{id}/submit")
    public SubmitResultDto submit(Principal principal, @PathVariable Long id,
                                  @RequestBody SubmitAnswersRequest req) {
        return service.submitAnswers(principal.getName(), id, req);
    }

    /** Teacher: activate quiz for a live session */
    @PostMapping("/{id}/activate")
    public void activate(Principal principal, @PathVariable Long id,
                         @RequestBody Map<String, Long> body) {
        service.activateQuiz(principal.getName(), id, body.get("sessionId"));
    }

    /** Teacher: deactivate quiz */
    @PostMapping("/{id}/deactivate")
    public void deactivate(Principal principal, @PathVariable Long id) {
        service.deactivateQuiz(principal.getName(), id);
    }

    /** Student: get active quiz for their current attendance session */
    @GetMapping("/active-for-session")
    public StudentAssignmentDto getActiveForSession(Principal principal,
                                                    @RequestParam Long sessionId) {
        return service.getActiveForSession(principal.getName(), sessionId);
    }

    /** Teacher: get open answers for a submission (for review) */
    @GetMapping("/submissions/{id}/answers")
    public List<ReviewAnswerDto> getAnswersForReview(Principal principal, @PathVariable Long id) {
        return service.getAnswersForReview(principal.getName(), id);
    }

    /** Teacher: submit review marks for open answers */
    @PostMapping("/submissions/{id}/review")
    public void reviewSubmission(Principal principal, @PathVariable Long id,
                                 @RequestBody ReviewSubmitRequest req) {
        service.reviewSubmission(principal.getName(), id, req);
    }

    /** Student: get new review notifications (reviewed but not yet seen) */
    @GetMapping("/new-reviews")
    public List<NewReviewNotificationDto> getNewReviews(Principal principal) {
        return service.getNewReviews(principal.getName());
    }

    /** Student: mark a review notification as seen */
    @PostMapping("/submissions/{id}/mark-seen")
    public void markSeen(Principal principal, @PathVariable Long id) {
        service.markNotificationSeen(principal.getName(), id);
    }
}
