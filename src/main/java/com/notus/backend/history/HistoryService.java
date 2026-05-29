package com.notus.backend.history;

import com.notus.backend.attendance.AttendanceRecord;
import com.notus.backend.attendance.AttendanceRecordRepository;
import com.notus.backend.attendance.AttendanceSession;
import com.notus.backend.attendance.AttendanceSessionRepository;
import com.notus.backend.history.dto.SessionStudentResultDto;
import com.notus.backend.history.dto.StudentHistoryItemDto;
import com.notus.backend.history.dto.TeacherHistoryItemDto;
import com.notus.backend.quiz.QuizAssignment;
import com.notus.backend.quiz.QuizAssignmentRepository;
import com.notus.backend.quiz.QuizSubmission;
import com.notus.backend.quiz.QuizSubmissionRepository;
import com.notus.backend.schedule.Schedule;
import com.notus.backend.schedule.ScheduleRepository;
import com.notus.backend.teachergroups.GroupMembershipRepository;
import com.notus.backend.teachergroups.GroupMembershipStatus;
import com.notus.backend.users.Student;
import com.notus.backend.users.StudentRepository;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class HistoryService {
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final QuizAssignmentRepository quizAssignmentRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final ScheduleRepository scheduleRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final GroupMembershipRepository groupMembershipRepository;

    public HistoryService(AttendanceSessionRepository attendanceSessionRepository,
                          AttendanceRecordRepository attendanceRecordRepository,
                          QuizAssignmentRepository quizAssignmentRepository,
                          QuizSubmissionRepository quizSubmissionRepository,
                          ScheduleRepository scheduleRepository,
                          TeacherRepository teacherRepository,
                          StudentRepository studentRepository,
                          GroupMembershipRepository groupMembershipRepository) {
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.quizAssignmentRepository = quizAssignmentRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.scheduleRepository = scheduleRepository;
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
        this.groupMembershipRepository = groupMembershipRepository;
    }

    public List<TeacherHistoryItemDto> getTeacherHistory(String teacherUid) {
        Teacher teacher = teacherRepository.findByClerkUserId(teacherUid)
                .orElse(null);
        if (teacher == null) return Collections.emptyList();

        List<AttendanceSession> sessions = attendanceSessionRepository.findByTeacher(teacher);
        List<QuizAssignment> assignments = quizAssignmentRepository.findByTeacher(teacher);

        Set<String> scheduleIds = new HashSet<>();
        Map<String, AttendanceSession> sessionMap = new HashMap<>();
        Map<String, QuizAssignment> assignmentMap = new HashMap<>();

        for (AttendanceSession s : sessions) {
            if (s.getSchedule() != null) {
                String sid = s.getSchedule().getId();
                scheduleIds.add(sid);
                sessionMap.put(sid, s);
            }
        }
        for (QuizAssignment a : assignments) {
            scheduleIds.add(a.getScheduleId());
            assignmentMap.put(a.getScheduleId(), a);
        }

        List<Schedule> schedules = scheduleRepository.findAllById(scheduleIds);
        List<TeacherHistoryItemDto> items = new ArrayList<>();

        for (Schedule sch : schedules) {
            String sid = sch.getId();
            AttendanceSession session = sessionMap.get(sid);
            QuizAssignment assignment = assignmentMap.get(sid);

            int attendanceCount = 0;
            if (session != null) {
                attendanceCount = attendanceRecordRepository.findBySessionId(session.getId()).size();
            }

            int submissionsCount = 0;
            int avgScore = 0;
            if (assignment != null) {
                List<QuizSubmission> subs = quizSubmissionRepository.findByAssignment(assignment);
                submissionsCount = subs.size();
                if (submissionsCount > 0) {
                    double totalPct = 0;
                    for (QuizSubmission sub : subs) {
                        if (sub.getTotal() > 0) {
                            totalPct += ((double) sub.getScore() / sub.getTotal()) * 100;
                        }
                    }
                    avgScore = (int) Math.round(totalPct / submissionsCount);
                }
            }

            TeacherHistoryItemDto item = TeacherHistoryItemDto.builder()
                    .scheduleId(sid)
                    .scheduleSubject(sch.getSubject())
                    .scheduleDate(sch.getDate())
                    .scheduleTime(sch.getTime())
                    .sessionId(session != null ? session.getId() : null)
                    .attendanceCount(attendanceCount)
                    .quizAssignmentId(assignment != null ? assignment.getId() : null)
                    .quizId(assignment != null && assignment.getQuiz() != null ? assignment.getQuiz().getId() : null)
                    .quizTitle(assignment != null && assignment.getQuiz() != null ? assignment.getQuiz().getTitle() : null)
                    .quizSubmissionCount(submissionsCount)
                    .quizAvgScore(avgScore)
                    .build();

            items.add(item);
        }

        items.sort((a,b) -> {
            if (a.getScheduleDate() == null) return 1;
            if (b.getScheduleDate() == null) return -1;
            return b.getScheduleDate().compareTo(a.getScheduleDate());
        });
        return items;
    }

    public List<StudentHistoryItemDto> getStudentHistory(String studentUid) {
        Student student = studentRepository.findByClerkUserId(studentUid)
                .orElse(null);
        if (student == null) return Collections.emptyList();

        List<AttendanceRecord> records = attendanceRecordRepository.findByStudent(student);
        List<QuizSubmission> submissions = quizSubmissionRepository.findByStudent(student);

        Set<String> scheduleIds = new HashSet<>();
        Map<String, AttendanceRecord> recordMap = new HashMap<>();
        Map<String, QuizSubmission> submissionMap = new HashMap<>();

        for (AttendanceRecord r : records) {
            AttendanceSession session = attendanceSessionRepository.findById(r.getSessionId()).orElse(null);
            if (session != null && session.getSchedule() != null) {
                String sid = session.getSchedule().getId();
                scheduleIds.add(sid);
                // In case of multiple checks by someone, we take the first or any.
                recordMap.put(sid, r);
            }
        }

        for (QuizSubmission s : submissions) {
            String sid = s.getAssignment().getScheduleId();
            scheduleIds.add(sid);
            submissionMap.put(sid, s);
        }

        List<Schedule> schedules = scheduleRepository.findAllById(scheduleIds);
        List<StudentHistoryItemDto> items = new ArrayList<>();

        for (Schedule sch : schedules) {
            String sid = sch.getId();
            AttendanceRecord record = recordMap.get(sid);
            QuizSubmission submission = submissionMap.get(sid);

            StudentHistoryItemDto item = StudentHistoryItemDto.builder()
                    .scheduleId(sid)
                    .scheduleSubject(sch.getSubject())
                    .scheduleDate(sch.getDate())
                    .scheduleTime(sch.getTime())
                    .attended(record != null)
                    .checkedInAt(record != null ? record.getCheckedInAt() : null)
                    .quizTitle(submission != null && submission.getAssignment().getQuiz() != null ? submission.getAssignment().getQuiz().getTitle() : null)
                    .quizAssignmentId(submission != null ? submission.getAssignment().getId() : null)
                    .quizId(submission != null && submission.getAssignment().getQuiz() != null ? submission.getAssignment().getQuiz().getId() : null)
                    .submissionId(submission != null ? submission.getId() : null)
                    .score(submission != null ? submission.getScore() : 0)
                    .total(submission != null ? submission.getTotal() : 0)
                    .pendingOpenReview(submission != null && submission.isPendingOpenReview())
                    .build();

            items.add(item);
        }

        items.sort((a,b) -> {
            if (a.getScheduleDate() == null) return 1;
            if (b.getScheduleDate() == null) return -1;
            return b.getScheduleDate().compareTo(a.getScheduleDate());
        });
        return items;
    }

    public List<SessionStudentResultDto> getSessionDetails(String scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow(() -> new RuntimeException("Schedule not found"));
        
        Set<Student> targetStudents = new HashSet<>();
        if (schedule.getTeacherGroup() != null) {
            groupMembershipRepository.findByGroupAndStatusOrderByJoinedAtAsc(schedule.getTeacherGroup(), GroupMembershipStatus.ACTIVE)
                    .forEach(membership -> targetStudents.add(membership.getStudent()));
        } else if (schedule.getStudentGroup() != null) {
            targetStudents.addAll(studentRepository.findByStudentGroupsId(schedule.getStudentGroup().getId()));
        }
        
        AttendanceSession session = attendanceSessionRepository.findFirstByScheduleIdOrderByCreatedAtDesc(scheduleId).orElse(null);
        QuizAssignment assignment = quizAssignmentRepository.findByScheduleId(scheduleId).orElse(null);
        
        List<AttendanceRecord> records = session != null ? attendanceRecordRepository.findBySessionId(session.getId()) : Collections.emptyList();
        List<QuizSubmission> submissions = assignment != null ? quizSubmissionRepository.findByAssignment(assignment) : Collections.emptyList();
        
        Map<Long, AttendanceRecord> recordMap = new HashMap<>();
        for (AttendanceRecord r : records) {
            recordMap.put(r.getStudent().getId(), r);
            targetStudents.add(r.getStudent());
        }
        
        Map<Long, QuizSubmission> submissionMap = new HashMap<>();
        for (QuizSubmission s : submissions) {
            submissionMap.put(s.getStudent().getId(), s);
            targetStudents.add(s.getStudent());
        }
        
        List<SessionStudentResultDto> results = new ArrayList<>();
        for (Student s : targetStudents) {
            AttendanceRecord r = recordMap.get(s.getId());
            QuizSubmission qs = submissionMap.get(s.getId());
            
            results.add(SessionStudentResultDto.builder()
                    .studentId(s.getId())
                    .studentName(s.getName())
                    .attended(r != null)
                    .submissionId(qs != null ? qs.getId() : null)
                    .quizScore(qs != null ? qs.getScore() : null)
                    .quizTotal(qs != null ? qs.getTotal() : null)
                    .pendingOpenReview(qs != null && qs.isPendingOpenReview())
                    .build());
        }
        
        results.sort(Comparator.comparing(SessionStudentResultDto::getStudentName));
        return results;
    }
}
