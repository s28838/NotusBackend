package com.notus.backend.schedule;

import com.notus.backend.attendance.group.StudentGroupRepository;
import com.notus.backend.teachergroups.GroupMembershipRepository;
import com.notus.backend.teachergroups.TeacherGroup;
import com.notus.backend.teachergroups.TeacherGroupRepository;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceWriteTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private StudentGroupRepository studentGroupRepository;
    @Mock private TeacherGroupRepository teacherGroupRepository;
    @Mock private GroupMembershipRepository groupMembershipRepository;

    @InjectMocks private ScheduleService scheduleService;

    @Test
    void getById_returnsSchedule_whenFound() {
        Schedule s = new Schedule();
        s.setId("abc");
        when(scheduleRepository.findById("abc")).thenReturn(Optional.of(s));

        Schedule result = scheduleService.getById("abc");

        assertThat(result.getId()).isEqualTo("abc");
    }

    @Test
    void getById_throws404_whenNotFound() {
        when(scheduleRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getById("x"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getSchedule_returnsEmptyForTeacherInsteadOfFallingBackToAllSchedules() {
        Instant start = Instant.parse("2026-05-01T00:00:00Z");
        Instant end = Instant.parse("2026-05-31T23:59:59Z");
        when(scheduleRepository.findByDateBetweenAndTeacherEntityIdOrderByTimeAsc(start, end, 99L))
                .thenReturn(List.of());

        List<Schedule> result = scheduleService.getSchedule(start, end, 99L, null, null);

        assertThat(result).isEmpty();
        verify(scheduleRepository, never()).findByDateBetweenOrderByTimeAsc(start, end);
    }

    @Test
    void getSchedule_returnsEmptyForTeacherGroupInsteadOfFallingBackToLegacyGroup() {
        Instant start = Instant.parse("2026-05-01T00:00:00Z");
        Instant end = Instant.parse("2026-05-31T23:59:59Z");
        when(scheduleRepository.findByDateBetweenAndTeacherGroup_IdOrderByTimeAsc(start, end, 10L))
                .thenReturn(List.of());
        when(teacherGroupRepository.existsById(10L)).thenReturn(true);

        List<Schedule> result = scheduleService.getSchedule(start, end, null, null, 10L);

        assertThat(result).isEmpty();
        verify(scheduleRepository, never()).findByDateBetweenAndStudentGroupIdOrderByTimeAsc(start, end, 10L);
        verify(scheduleRepository, never()).findByDateBetweenOrderByTimeAsc(start, end);
    }

    @Test
    void createSchedule_savesAndReturnsSchedule() {
        Teacher teacher = teacher(1L);
        TeacherGroup group = teacherGroup(10L, teacher);
        when(teacherRepository.findByClerkUserId("uid1")).thenReturn(Optional.of(teacher));
        when(teacherGroupRepository.findByIdAndTeacherAndActiveTrue(10L, teacher)).thenReturn(Optional.of(group));

        Schedule saved = new Schedule();
        saved.setId("new-id");
        when(scheduleRepository.save(any(Schedule.class))).thenReturn(saved);

        Schedule result = scheduleService.createSchedule(request(10L), "uid1");

        assertThat(result.getId()).isEqualTo("new-id");
        verify(scheduleRepository).save(any(Schedule.class));
    }

    @Test
    void createSchedule_throws400_whenGroupMissing() {
        Teacher teacher = teacher(1L);
        when(teacherRepository.findByClerkUserId("uid1")).thenReturn(Optional.of(teacher));

        CreateScheduleRequest req = new CreateScheduleRequest(
                "Matematyka", Instant.now(), "08:00 - 09:30", "101", "Wyklad", null, null, null,
                false, 1, null
        );

        assertThatThrownBy(() -> scheduleService.createSchedule(req, "uid1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createSchedule_throws403_whenTeacherNotFound() {
        when(teacherRepository.findByClerkUserId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.createSchedule(request(10L), "unknown"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createSchedule_createsWeeklySeriesUntilEndDate() {
        Teacher teacher = teacher(1L);
        TeacherGroup group = teacherGroup(10L, teacher);
        when(teacherRepository.findByClerkUserId("uid1")).thenReturn(Optional.of(teacher));
        when(teacherGroupRepository.findByIdAndTeacherAndActiveTrue(10L, teacher)).thenReturn(Optional.of(group));
        when(scheduleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CreateScheduleRequest req = new CreateScheduleRequest(
                "Matematyka",
                Instant.parse("2026-05-04T10:00:00Z"),
                "08:00 - 09:30",
                "101",
                "Wyklad",
                null,
                10L,
                null,
                true,
                1,
                Instant.parse("2026-05-18T10:00:00Z")
        );

        Schedule result = scheduleService.createSchedule(req, "uid1");

        assertThat(result.getDate()).isEqualTo(Instant.parse("2026-05-04T10:00:00Z"));
        verify(scheduleRepository).saveAll(org.mockito.ArgumentMatchers.argThat(schedules -> {
            List<Schedule> list = (List<Schedule>) schedules;
            return list.size() == 3
                    && list.get(0).getDate().equals(Instant.parse("2026-05-04T10:00:00Z"))
                    && list.get(1).getDate().equals(Instant.parse("2026-05-11T10:00:00Z"))
                    && list.get(2).getDate().equals(Instant.parse("2026-05-18T10:00:00Z"))
                    && list.stream().allMatch(schedule -> schedule.getRecurrenceSeriesId() != null)
                    && list.stream().allMatch(schedule -> Integer.valueOf(1).equals(schedule.getRepeatEveryWeeks()))
                    && list.stream().allMatch(schedule -> schedule.getRecurrenceEndsAt().equals(Instant.parse("2026-05-18T10:00:00Z")));
        }));
        verify(scheduleRepository, never()).save(any(Schedule.class));
    }

    @Test
    void createSchedule_throws400_whenWeeklyEndDateIsBeforeStart() {
        Teacher teacher = teacher(1L);
        TeacherGroup group = teacherGroup(10L, teacher);
        when(teacherRepository.findByClerkUserId("uid1")).thenReturn(Optional.of(teacher));
        when(teacherGroupRepository.findByIdAndTeacherAndActiveTrue(10L, teacher)).thenReturn(Optional.of(group));

        CreateScheduleRequest req = new CreateScheduleRequest(
                "Matematyka",
                Instant.parse("2026-05-04T10:00:00Z"),
                "08:00 - 09:30",
                "101",
                "Wyklad",
                null,
                10L,
                null,
                true,
                1,
                Instant.parse("2026-05-03T10:00:00Z")
        );

        assertThatThrownBy(() -> scheduleService.createSchedule(req, "uid1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(scheduleRepository, never()).save(any(Schedule.class));
        verify(scheduleRepository, never()).saveAll(anyList());
    }

    @Test
    void updateSchedule_updatesFields() {
        Teacher teacher = teacher(1L);
        TeacherGroup group = teacherGroup(10L, teacher);
        Schedule existing = schedule("id1", teacher);
        existing.setSubject("Old Subject");
        when(scheduleRepository.findById("id1")).thenReturn(Optional.of(existing));
        when(teacherRepository.findByClerkUserId("uid1")).thenReturn(Optional.of(teacher));
        when(teacherGroupRepository.findByIdAndTeacherAndActiveTrue(10L, teacher)).thenReturn(Optional.of(group));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

        Schedule result = scheduleService.updateSchedule("id1", request(10L), "uid1");

        assertThat(result.getSubject()).isEqualTo("Matematyka");
        assertThat(result.getRoom()).isEqualTo("101");
        assertThat(result.getTeacherGroup()).isSameAs(group);
    }

    @Test
    void updateSchedule_throws404_whenNotFound() {
        when(scheduleRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.updateSchedule("x", request(10L), "uid1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateSchedule_preservesTeacherEntity() {
        Teacher teacher = teacher(5L);
        TeacherGroup group = teacherGroup(10L, teacher);
        Schedule existing = schedule("id2", teacher);
        existing.setSubject("Old");
        when(scheduleRepository.findById("id2")).thenReturn(Optional.of(existing));
        when(teacherRepository.findByClerkUserId("uid1")).thenReturn(Optional.of(teacher));
        when(teacherGroupRepository.findByIdAndTeacherAndActiveTrue(10L, teacher)).thenReturn(Optional.of(group));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

        Schedule result = scheduleService.updateSchedule("id2", request(10L), "uid1");

        assertThat(result.getTeacherEntity()).isSameAs(teacher);
    }

    @Test
    void deleteSchedule_deletesWhenFound() {
        Teacher teacher = teacher(1L);
        when(scheduleRepository.findById("id1")).thenReturn(Optional.of(schedule("id1", teacher)));
        when(teacherRepository.findByClerkUserId("uid1")).thenReturn(Optional.of(teacher));

        scheduleService.deleteSchedule("id1", "uid1");

        verify(scheduleRepository).deleteById("id1");
    }

    @Test
    void deleteSchedule_deletesFutureOccurrencesForRecurringLesson() {
        Teacher teacher = teacher(1L);
        Schedule first = schedule("id1", teacher);
        first.setDate(Instant.parse("2026-05-04T10:00:00Z"));
        first.setRecurrenceSeriesId("series-1");
        Schedule second = schedule("id2", teacher);
        second.setDate(Instant.parse("2026-05-11T10:00:00Z"));
        second.setRecurrenceSeriesId("series-1");
        when(scheduleRepository.findById("id1")).thenReturn(Optional.of(first));
        when(teacherRepository.findByClerkUserId("uid1")).thenReturn(Optional.of(teacher));
        when(scheduleRepository.findByRecurrenceSeriesIdAndTeacherEntityAndDateGreaterThanEqualOrderByDateAscTimeAsc(
                "series-1",
                teacher,
                Instant.parse("2026-05-04T10:00:00Z")
        )).thenReturn(List.of(first, second));

        scheduleService.deleteSchedule("id1", "uid1", true);

        verify(scheduleRepository).deleteAll(List.of(first, second));
        verify(scheduleRepository, never()).deleteById("id1");
    }

    @Test
    void deleteSchedule_deletesSingleRecurringOccurrenceWhenFutureFlagIsFalse() {
        Teacher teacher = teacher(1L);
        Schedule first = schedule("id1", teacher);
        first.setRecurrenceSeriesId("series-1");
        when(scheduleRepository.findById("id1")).thenReturn(Optional.of(first));
        when(teacherRepository.findByClerkUserId("uid1")).thenReturn(Optional.of(teacher));

        scheduleService.deleteSchedule("id1", "uid1", false);

        verify(scheduleRepository).deleteById("id1");
        verify(scheduleRepository, never()).deleteAll(anyList());
    }

    @Test
    void deleteSchedule_throws404_whenNotFound() {
        when(scheduleRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.deleteSchedule("x", "uid1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private CreateScheduleRequest request(Long teacherGroupId) {
        return new CreateScheduleRequest(
                "Matematyka", Instant.now(), "08:00 - 09:30", "101", "Wyklad", null, teacherGroupId, null,
                false, 1, null
        );
    }

    private Teacher teacher(Long id) {
        Teacher teacher = new Teacher();
        teacher.setId(id);
        teacher.setName("Jan Kowalski");
        return teacher;
    }

    private TeacherGroup teacherGroup(Long id, Teacher teacher) {
        TeacherGroup group = new TeacherGroup();
        group.setId(id);
        group.setTeacher(teacher);
        group.setName("Matematyka 1A");
        group.setSubject("Matematyka");
        group.setActive(true);
        return group;
    }

    private Schedule schedule(String id, Teacher teacher) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setTeacherEntity(teacher);
        return schedule;
    }
}
