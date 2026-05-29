package com.notus.backend.attendance;

import com.notus.backend.attendance.dto.CheckInRequest;
import com.notus.backend.attendance.dto.CheckInResponse;
import com.notus.backend.attendance.dto.CreateSessionRequest;
import com.notus.backend.attendance.dto.CreateSessionResponse;
import com.notus.backend.attendance.dto.QrResponse;
import com.notus.backend.attendance.dto.AttendanceSessionSummaryDto;
import com.notus.backend.realtime.TeacherRealtimeService;
import com.notus.backend.schedule.Schedule;
import com.notus.backend.schedule.ScheduleRepository;
import com.notus.backend.teachergroups.TeacherGroup;
import com.notus.backend.users.Student;
import com.notus.backend.users.StudentRepository;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceSessionRepository sessionRepo;

    @Mock
    private AttendanceRecordRepository recordRepo;

    @Mock
    private QrTokenService qrTokenService;

    @Mock
    private QrImageService qrImageService;

    @Mock
    private StudentRepository studentRepo;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private TeacherRealtimeService realtimeService;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    void shouldCreateSession() {
        // given
        String teacherUid = "teacher-123";
        String scheduleId = "schedule-1";

        CreateSessionRequest request = new CreateSessionRequest(scheduleId);

        Teacher teacher = new Teacher();
        teacher.setId(10L);

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTeacherEntity(teacher);
        schedule.setSubject("Matematyka");
        schedule.setRoom("101");
        schedule.setTime("08:00");
        schedule.setDate(Instant.parse("2025-01-10T08:00:00Z"));

        AttendanceSession savedSession = new AttendanceSession();
        savedSession.setId(100L);
        savedSession.setTeacher(teacher);
        savedSession.setSchedule(schedule);
        savedSession.setCreatedAt(schedule.getDate());
        savedSession.setActive(true);
        savedSession.setShortCode("ABC123");

        when(teacherRepo.findByClerkUserId(teacherUid))
                .thenReturn(Optional.of(teacher));

        when(scheduleRepository.findById(scheduleId))
                .thenReturn(Optional.of(schedule));

        when(sessionRepo.save(any(AttendanceSession.class)))
                .thenReturn(savedSession);

        // when
        CreateSessionResponse result = attendanceService.createSession(teacherUid, request);

        // then
        assertNotNull(result);
        assertEquals(100L, result.sessionId());
        assertEquals(scheduleId, result.scheduleId());
        assertEquals("Matematyka", result.title());
        assertEquals("101", result.room());
        assertEquals("08:00", result.time());
        assertEquals(schedule.getDate(), result.createdAt());
        assertTrue(result.active());

        verify(teacherRepo).findByClerkUserId(teacherUid);
        verify(scheduleRepository).findById(scheduleId);
        verify(sessionRepo).save(any(AttendanceSession.class));
    }

    @Test
    void shouldCheckInUsingShortCode() {
        // given
        String studentUid = "student-1";
        String code = "ABC123";
        CheckInRequest req = new CheckInRequest(null, code); // brak tokena QR, tylko kod

        AttendanceSession session = new AttendanceSession();
        session.setId(100L);
        session.setActive(true);

        Student student = new Student();
        student.setClerkUserId(studentUid);

        when(sessionRepo.findByShortCode(code)).thenReturn(Optional.of(session));
        when(studentRepo.findByClerkUserId(studentUid)).thenReturn(Optional.of(student));
        when(recordRepo.findBySessionIdAndStudent(any(), any())).thenReturn(Optional.empty());
        when(recordRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CheckInResponse response = attendanceService.checkIn(studentUid, req);

        // then
        assertNotNull(response);
        assertFalse(response.alreadyCheckIn());
        verify(recordRepo).save(any(AttendanceRecord.class));
    }
    @Test
    void shouldThrowExceptionWhenSessionIsInactive() {
        // given
        String studentUid = "student-1";
        String code = "ABC123";
        CheckInRequest req = new CheckInRequest(null, code);

        AttendanceSession inactiveSession = new AttendanceSession();
        inactiveSession.setId(200L);
        inactiveSession.setActive(false);

        when(sessionRepo.findByShortCode(code.toUpperCase()))
                .thenReturn(Optional.of(inactiveSession));

        // when & then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            attendanceService.checkIn(studentUid, req);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Sesja nieaktywna", exception.getReason()); //

        verify(recordRepo, never()).save(any());
    }

    @Test
    void shouldNotCreateDuplicateRecordWhenStudentAlreadyCheckedIn() {
        // given
        String studentUid = "student-1";
        CheckInRequest req = new CheckInRequest(null, "ABC123");

        AttendanceSession session = new AttendanceSession();
        session.setId(100L);
        session.setActive(true);
        session.setShortCode("ABC123");

        Student student = new Student();
        student.setClerkUserId(studentUid);
        student.setName("Jan Kowalski");

        AttendanceRecord existingRecord = new AttendanceRecord();
        existingRecord.setSessionId(100L);
        existingRecord.setStudent(student);
        existingRecord.setCheckedInAt(Instant.now());

        when(sessionRepo.findByShortCode("ABC123")).thenReturn(Optional.of(session));
        when(studentRepo.findByClerkUserId(studentUid)).thenReturn(Optional.of(student));
        when(recordRepo.findBySessionIdAndStudent(100L, student))
                .thenReturn(Optional.of(existingRecord));

        // when
        CheckInResponse response = attendanceService.checkIn(studentUid, req);

        // then
        assertNotNull(response);
        assertTrue(response.alreadyCheckIn(), "Flaga alreadyCheckIn powinna być true");
        assertEquals("Jan Kowalski", response.studentName());

        verify(recordRepo, never()).save(any(AttendanceRecord.class));
    }

    @Test
    void createSession_withValidTime_setsEndsAt() {
        Teacher teacher = new Teacher();
        teacher.setId(1L);
        teacher.setClerkUserId("uid_teacher");

        Schedule schedule = new Schedule();
        schedule.setId("sched_1");
        schedule.setSubject("Math");
        schedule.setRoom("101");
        // date = 2026-04-06 midnight UTC → 2026-04-06T00:00:00Z
        schedule.setDate(Instant.parse("2026-04-06T00:00:00Z"));
        schedule.setTime("10:15 - 12:00");
        schedule.setTeacherEntity(teacher);

        when(teacherRepo.findByClerkUserId("uid_teacher")).thenReturn(Optional.of(teacher));
        when(scheduleRepository.findById("sched_1")).thenReturn(Optional.of(schedule));
        when(sessionRepo.save(any(AttendanceSession.class))).thenAnswer(inv -> {
            AttendanceSession s = inv.getArgument(0);
            s.setId(42L);
            return s;
        });

        CreateSessionRequest req = new CreateSessionRequest("sched_1");
        attendanceService.createSession("uid_teacher", req);

        ArgumentCaptor<AttendanceSession> captor = ArgumentCaptor.forClass(AttendanceSession.class);
        verify(sessionRepo).save(captor.capture());
        AttendanceSession saved = captor.getValue();

        assertNotNull(saved.getEndsAt());
        // 12:00 Warsaw on 2026-04-06 = 10:00 UTC (CEST = UTC+2)
        assertEquals(Instant.parse("2026-04-06T10:00:00Z"), saved.getEndsAt());
    }

    @Test
    void createSession_withNullTime_endsAtIsNull() {
        Teacher teacher = new Teacher();
        teacher.setId(1L);
        teacher.setClerkUserId("uid_teacher");

        Schedule schedule = new Schedule();
        schedule.setId("sched_2");
        schedule.setSubject("Math");
        schedule.setRoom("101");
        schedule.setDate(Instant.parse("2026-04-06T00:00:00Z"));
        schedule.setTime(null); // no time set
        schedule.setTeacherEntity(teacher);

        when(teacherRepo.findByClerkUserId("uid_teacher")).thenReturn(Optional.of(teacher));
        when(scheduleRepository.findById("sched_2")).thenReturn(Optional.of(schedule));
        when(sessionRepo.save(any(AttendanceSession.class))).thenAnswer(inv -> {
            AttendanceSession s = inv.getArgument(0);
            s.setId(43L);
            return s;
        });

        CreateSessionRequest req = new CreateSessionRequest("sched_2");
        attendanceService.createSession("uid_teacher", req);

        ArgumentCaptor<AttendanceSession> captor = ArgumentCaptor.forClass(AttendanceSession.class);
        verify(sessionRepo).save(captor.capture());
        assertNull(captor.getValue().getEndsAt());
    }

    @Test
    void generateQr_withEndsAt_returnsSessionEndsAt() {
        Teacher teacher = new Teacher(); teacher.setId(1L); teacher.setClerkUserId("uid");
        AttendanceSession session = new AttendanceSession();
        session.setId(7L); session.setActive(true); session.setShortCode("XYZ");
        Instant endsAt = Instant.parse("2026-04-07T12:00:00Z");
        session.setEndsAt(endsAt);

        when(teacherRepo.findByClerkUserId("uid")).thenReturn(Optional.of(teacher));
        when(sessionRepo.findByIdAndTeacher(7L, teacher)).thenReturn(Optional.of(session));
        when(qrTokenService.createToken(7L)).thenReturn("tok");
        when(qrTokenService.ttlSeconds()).thenReturn(300L);
        when(qrImageService.toPngBase64("tok", 320)).thenReturn("base64==");

        QrResponse resp = attendanceService.generateQr("uid", 7L);

        assertEquals(endsAt.getEpochSecond(), resp.sessionEndsAt());
    }

    @Test
    void generateQr_withNullEndsAt_fallsBackToExpiresAt() {
        Teacher teacher = new Teacher(); teacher.setId(1L); teacher.setClerkUserId("uid");
        AttendanceSession session = new AttendanceSession();
        session.setId(8L); session.setActive(true); session.setShortCode("XYZ");
        session.setEndsAt(null);

        when(teacherRepo.findByClerkUserId("uid")).thenReturn(Optional.of(teacher));
        when(sessionRepo.findByIdAndTeacher(8L, teacher)).thenReturn(Optional.of(session));
        when(qrTokenService.createToken(8L)).thenReturn("tok");
        when(qrTokenService.ttlSeconds()).thenReturn(300L);
        when(qrImageService.toPngBase64("tok", 320)).thenReturn("base64==");

        QrResponse resp = attendanceService.generateQr("uid", 8L);

        assertTrue(resp.sessionEndsAt() >= Instant.now().getEpochSecond() + 290);
        assertEquals(resp.expiresAtEpochSeconds(), resp.sessionEndsAt());
    }

    @Test
    void getSessionSummary_returnsGroupScopedSessionNumber() {
        Teacher teacher = new Teacher();
        teacher.setId(1L);
        teacher.setClerkUserId("uid_teacher");

        TeacherGroup groupA = new TeacherGroup();
        groupA.setId(10L);
        groupA.setName("AAI1");

        TeacherGroup groupB = new TeacherGroup();
        groupB.setId(20L);
        groupB.setName("BBI1");

        Schedule firstGroupLesson = new Schedule();
        firstGroupLesson.setId("sched_1");
        firstGroupLesson.setSubject("Matematyka");
        firstGroupLesson.setDate(Instant.parse("2026-05-01T08:00:00Z"));
        firstGroupLesson.setTeacherEntity(teacher);
        firstGroupLesson.setTeacherGroup(groupA);

        Schedule secondGroupLesson = new Schedule();
        secondGroupLesson.setId("sched_2");
        secondGroupLesson.setSubject("Matematyka");
        secondGroupLesson.setDate(Instant.parse("2026-05-08T08:00:00Z"));
        secondGroupLesson.setTeacherEntity(teacher);
        secondGroupLesson.setTeacherGroup(groupA);

        Schedule otherGroupLesson = new Schedule();
        otherGroupLesson.setId("sched_3");
        otherGroupLesson.setSubject("Matematyka");
        otherGroupLesson.setDate(Instant.parse("2026-05-02T08:00:00Z"));
        otherGroupLesson.setTeacherEntity(teacher);
        otherGroupLesson.setTeacherGroup(groupB);

        AttendanceSession first = new AttendanceSession();
        first.setId(14L);
        first.setTeacher(teacher);
        first.setSchedule(firstGroupLesson);
        first.setCreatedAt(firstGroupLesson.getDate());
        first.setActive(false);

        AttendanceSession second = new AttendanceSession();
        second.setId(22L);
        second.setTeacher(teacher);
        second.setSchedule(secondGroupLesson);
        second.setCreatedAt(secondGroupLesson.getDate());
        second.setActive(true);

        AttendanceSession other = new AttendanceSession();
        other.setId(15L);
        other.setTeacher(teacher);
        other.setSchedule(otherGroupLesson);
        other.setCreatedAt(otherGroupLesson.getDate());
        other.setActive(false);

        when(teacherRepo.findByClerkUserId("uid_teacher")).thenReturn(Optional.of(teacher));
        when(sessionRepo.findByIdAndTeacher(22L, teacher)).thenReturn(Optional.of(second));
        when(sessionRepo.findByTeacher(teacher)).thenReturn(List.of(first, other, second));

        AttendanceSessionSummaryDto summary = attendanceService.getSessionSummary("uid_teacher", 22L);

        assertEquals(22L, summary.sessionId());
        assertEquals(2, summary.groupSessionNumber());
        assertEquals("Matematyka", summary.sessionTitle());
        assertEquals("AAI1", summary.groupName());
    }
}
