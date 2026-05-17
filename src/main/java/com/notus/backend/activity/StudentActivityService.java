package com.notus.backend.activity;

import com.notus.backend.activity.dto.TeacherActivityItemResponse;
import com.notus.backend.activity.dto.TeacherActivityResponse;
import com.notus.backend.activity.dto.TeacherNotificationResponse;
import com.notus.backend.activity.dto.TeacherNotificationsResponse;
import com.notus.backend.attendance.AttendanceRecordRepository;
import com.notus.backend.attendance.AttendanceSessionRepository;
import com.notus.backend.grades.GradeRepository;
import com.notus.backend.quiz.QuizSubmissionRepository;
import com.notus.backend.teachergroups.GroupMembershipRepository;
import com.notus.backend.teachergroups.GroupMembershipStatus;
import com.notus.backend.users.Student;
import com.notus.backend.users.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class StudentActivityService {

    private final StudentRepository studentRepository;
    private final GradeRepository gradeRepository;
    private final GroupMembershipRepository membershipRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;

    public StudentActivityService(StudentRepository studentRepository,
                                  GradeRepository gradeRepository,
                                  GroupMembershipRepository membershipRepository,
                                  AttendanceRecordRepository attendanceRecordRepository,
                                  AttendanceSessionRepository attendanceSessionRepository,
                                  QuizSubmissionRepository quizSubmissionRepository) {
        this.studentRepository = studentRepository;
        this.gradeRepository = gradeRepository;
        this.membershipRepository = membershipRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
    }

    @Transactional(readOnly = true)
    public TeacherNotificationsResponse notifications(String studentUid) {
        Student student = currentStudent(studentUid);
        Instant now = Instant.now();
        List<TeacherNotificationResponse> notifications = new ArrayList<>();

        gradeRepository.findByClerkUserIdAndDeletedAtIsNullOrderByIssueDateDesc(studentUid).stream()
                .filter(grade -> grade.isNew())
                .limit(10)
                .forEach(grade -> notifications.add(new TeacherNotificationResponse(
                        "student-grade-" + grade.getId(),
                        "GRADE",
                        "Nowa ocena",
                        grade.getSubject() + ": " + grade.getValue(),
                        "success",
                        false,
                        toInstant(grade.getIssueDate()),
                        grade.getGroup() != null ? "/student/groups/" + grade.getGroup().getId() + "/grades" : "/student/groups"
                )));

        quizSubmissionRepository.findByStudentAndReviewedAtIsNotNullAndNotificationSeenFalse(student).stream()
                .limit(10)
                .forEach(submission -> notifications.add(new TeacherNotificationResponse(
                        "student-review-" + submission.getId(),
                        "QUIZ_REVIEW",
                        "Quiz został oceniony",
                        submission.getAssignment().getQuiz().getTitle(),
                        "info",
                        false,
                        submission.getReviewedAt(),
                        "/student/quiz-review/" + submission.getAssignment().getId()
                )));

        notifications.sort(Comparator.comparing(TeacherNotificationResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        List<TeacherNotificationResponse> limited = notifications.stream().limit(12).toList();
        return new TeacherNotificationsResponse(limited, limited.size(), now);
    }

    @Transactional(readOnly = true)
    public TeacherActivityResponse activity(String studentUid) {
        Student student = currentStudent(studentUid);
        List<TeacherActivityItemResponse> items = new ArrayList<>();

        gradeRepository.findByClerkUserIdAndDeletedAtIsNullOrderByIssueDateDesc(studentUid).stream()
                .limit(12)
                .forEach(grade -> items.add(new TeacherActivityItemResponse(
                        "student-grade-" + grade.getId(),
                        "GRADE",
                        "Wystawiono ocenę " + grade.getValue(),
                        (grade.getGroup() != null ? grade.getGroup().getName() : grade.getSubject()) + " | " + sourceLabel(grade.getTitle(), grade.getDescription()),
                        "grade",
                        toInstant(grade.getIssueDate()),
                        grade.getGroup() != null ? "/student/groups/" + grade.getGroup().getId() + "/grades" : "/student/groups"
                )));

        membershipRepository.findByStudentAndStatusOrderByJoinedAtDesc(student, GroupMembershipStatus.ACTIVE).stream()
                .limit(10)
                .forEach(membership -> items.add(new TeacherActivityItemResponse(
                        "student-membership-" + membership.getId(),
                        "GROUP_MEMBER",
                        "Dołączono do grupy",
                        membership.getGroup().getName(),
                        "groups",
                        membership.getJoinedAt(),
                        "/student/groups"
                )));

        attendanceRecordRepository.findByStudent(student).stream()
                .limit(10)
                .forEach(record -> {
                    String title = attendanceSessionRepository.findById(record.getSessionId())
                            .map(session -> session.getTitle() != null ? session.getTitle() : "Sesja obecności")
                            .orElse("Sesja obecności");
                    items.add(new TeacherActivityItemResponse(
                            "student-attendance-" + record.getId(),
                            "ATTENDANCE",
                            "Zapisano obecność",
                            title,
                            "fact_check",
                            record.getCheckedInAt(),
                            "/student/schedule"
                    ));
                });

        quizSubmissionRepository.findByStudent(student).stream()
                .limit(10)
                .forEach(submission -> items.add(new TeacherActivityItemResponse(
                        "student-quiz-" + submission.getId(),
                        "QUIZ_SUBMISSION",
                        "Rozwiązano quiz",
                        submission.getAssignment().getQuiz().getTitle() + " | " + submission.getScore() + "/" + submission.getTotal() + " pkt",
                        "quiz",
                        submission.getSubmittedAt(),
                        "/student/quiz-review/" + submission.getAssignment().getId()
                )));

        List<TeacherActivityItemResponse> sorted = items.stream()
                .filter(item -> item.occurredAt() != null)
                .sorted(Comparator.comparing(TeacherActivityItemResponse::occurredAt).reversed())
                .limit(30)
                .toList();
        return new TeacherActivityResponse(sorted, Instant.now());
    }

    private Student currentStudent(String uid) {
        return studentRepository.findByClerkUserId(uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono ucznia."));
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private String sourceLabel(String title, String description) {
        if (title != null && !title.isBlank() && description != null && !description.isBlank()) {
            return title + ": " + description;
        }
        if (title != null && !title.isBlank()) return title;
        if (description != null && !description.isBlank()) return description;
        return "Ocena";
    }
}
