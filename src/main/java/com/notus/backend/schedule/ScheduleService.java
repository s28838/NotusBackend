package com.notus.backend.schedule;

import com.notus.backend.attendance.group.StudentGroup;
import com.notus.backend.attendance.group.StudentGroupRepository;
import com.notus.backend.realtime.TeacherRealtimeService;
import com.notus.backend.realtime.dto.TeacherRealtimeEvent;
import com.notus.backend.teachergroups.GroupMembershipRepository;
import com.notus.backend.teachergroups.GroupMembershipStatus;
import com.notus.backend.teachergroups.TeacherGroup;
import com.notus.backend.teachergroups.TeacherGroupRepository;
import com.notus.backend.users.Student;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final TeacherRepository teacherRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final TeacherGroupRepository teacherGroupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final TeacherRealtimeService teacherRealtimeService;

    public List<Schedule> getTodaySchedule(Long teacherId, String teacherName, Long groupId) {
        LocalDate today = LocalDate.now();
        Instant start = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        return getFilteredSchedule(start, end, teacherId, teacherName, groupId);
    }

    public List<Schedule> getScheduleByDay(LocalDate date) {
        Instant start = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        return scheduleRepository.findByDateBetweenAndDeletedFalseOrderByTimeAsc(start, end);
    }

    public List<Schedule> getSchedule(Instant start, Instant end, Long teacherId, String teacherName, Long groupId) {
        return getFilteredSchedule(start, end, teacherId, teacherName, groupId);
    }

    public void assertTeacherGroupOwned(Long groupId, Long teacherId) {
        if (groupId == null) {
            return;
        }
        TeacherGroup group = teacherGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher group not found"));
        if (group.getTeacher() == null || !group.getTeacher().getId().equals(teacherId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nie możesz pobrać planu cudzej grupy.");
        }
    }

    public Schedule getNextSchedule(Long teacherId, String teacherName, Long groupId, Student student) {
        Instant now = Instant.now();
        Instant end = now.plus(java.time.Duration.ofDays(7));
        
        List<Schedule> schedules;
        if (student != null) {
            schedules = getScheduleForStudentInRange(student, now.minus(java.time.Duration.ofDays(1)), end);
        } else {
            schedules = getFilteredSchedule(now.minus(java.time.Duration.ofDays(1)), end, teacherId, teacherName, groupId);
        }

        java.time.LocalTime currentTime = java.time.LocalTime.now();
        LocalDate currentDate = LocalDate.now();

        for (Schedule s : schedules) {
            LocalDate sDate = LocalDate.ofInstant(s.getDate(), ZoneId.systemDefault());
            if (sDate.isBefore(currentDate)) continue;

            if (sDate.isEqual(currentDate)) {
                if (s.getTime() != null && s.getTime().contains(" - ")) {
                    String[] parts = s.getTime().split(" - ");
                    if (parts.length == 2) {
                        try {
                            java.time.LocalTime endTime = java.time.LocalTime.parse(parts[1]);
                            if (endTime.isAfter(currentTime)) {
                                return s;
                            }
                        } catch (Exception e) {
                            // ignore parse error
                        }
                    }
                }
            } else {
                return s; 
            }
        }
        return null;
    }

    public List<Schedule> getScheduleForStudentInRange(Student student, Instant start, Instant end) {
        if (student == null) {
            return List.of();
        }

        List<Long> teacherGroupIds = groupMembershipRepository
                .findByStudentAndStatusOrderByJoinedAtDesc(student, GroupMembershipStatus.ACTIVE)
                .stream()
                .map(membership -> membership.getGroup().getId())
                .distinct()
                .toList();
        if (!teacherGroupIds.isEmpty()) {
            return scheduleRepository.findByDateBetweenAndTeacherGroup_IdInAndDeletedFalseOrderByTimeAsc(start, end, teacherGroupIds);
        }

        if (student.getStudentGroups() == null || student.getStudentGroups().isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = student.getStudentGroups()
                .stream()
                .map(group -> group.getId())
                .distinct()
                .toList();
        return scheduleRepository.findByDateBetweenAndStudentGroupIdInAndDeletedFalseOrderByTimeAsc(start, end, groupIds);
    }

    public List<Schedule> getScheduleForStudent(Student student) {
        if (student == null) {
            return List.of();
        }

        List<Long> teacherGroupIds = groupMembershipRepository
                .findByStudentAndStatusOrderByJoinedAtDesc(student, GroupMembershipStatus.ACTIVE)
                .stream()
                .map(membership -> membership.getGroup().getId())
                .distinct()
                .toList();
        if (!teacherGroupIds.isEmpty()) {
            return scheduleRepository.findByTeacherGroup_IdInAndDeletedFalseOrderByDateAscTimeAsc(teacherGroupIds);
        }

        if (student.getStudentGroups() == null || student.getStudentGroups().isEmpty()) {
            return List.of();
        }

        List<Long> groupIds = student.getStudentGroups()
                .stream()
                .map(group -> group.getId())
                .distinct()
                .toList();

        return scheduleRepository.findByStudentGroupIdInAndDeletedFalseOrderByDateAscTimeAsc(groupIds);
    }

    private List<Schedule> getFilteredSchedule(
            Instant start,
            Instant end,
            Long teacherId,
            String teacherName,
            Long groupId
    ) {
        if (groupId != null) {
            List<Schedule> byTeacherGroup = scheduleRepository.findByDateBetweenAndTeacherGroup_IdAndDeletedFalseOrderByTimeAsc(start, end, groupId);
            if (!byTeacherGroup.isEmpty() || teacherGroupRepository.existsById(groupId)) {
                return byTeacherGroup;
            }
            return scheduleRepository.findByDateBetweenAndStudentGroupIdAndDeletedFalseOrderByTimeAsc(start, end, groupId);
        }

        if (teacherId != null) {
            return scheduleRepository.findByDateBetweenAndTeacherEntityIdAndDeletedFalseOrderByTimeAsc(start, end, teacherId);
        }

        if (teacherName != null && !teacherName.isBlank()) {
            return scheduleRepository.findByDateBetweenAndTeacherEntityNameContainingIgnoreCaseAndDeletedFalseOrderByTimeAsc(start, end, teacherName);
        }

        return scheduleRepository.findByDateBetweenAndDeletedFalseOrderByTimeAsc(start, end);
    }

    public List<Schedule> getTodayScheduleForStudent(Student student) {
        if (student == null) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        Instant start = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<Long> teacherGroupIds = groupMembershipRepository
                .findByStudentAndStatusOrderByJoinedAtDesc(student, GroupMembershipStatus.ACTIVE)
                .stream()
                .map(membership -> membership.getGroup().getId())
                .distinct()
                .toList();
        if (!teacherGroupIds.isEmpty()) {
            return scheduleRepository.findByDateBetweenAndTeacherGroup_IdInAndDeletedFalseOrderByTimeAsc(start, end, teacherGroupIds);
        }

        if (student.getStudentGroups() == null || student.getStudentGroups().isEmpty()) {
            return List.of();
        }

        List<Long> groupIds = student.getStudentGroups()
                .stream()
                .map(group -> group.getId())
                .distinct()
                .toList();

        return scheduleRepository.findByDateBetweenAndStudentGroupIdInAndDeletedFalseOrderByTimeAsc(start, end, groupIds);
    }


    @Transactional(readOnly = true)
    public Schedule getById(String id) {
        return scheduleRepository.findById(id)
                .filter(schedule -> !schedule.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));
    }

    @Transactional
    public Schedule createSchedule(CreateScheduleRequest req, String clerkUid) {
        Teacher teacher = teacherRepository.findByClerkUserId(clerkUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Teacher not found"));

        StudentGroup group = null;
        if (req.studentGroupId() != null) {
            group = studentGroupRepository.findById(req.studentGroupId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student group not found"));
        }
        Long teacherGroupId = req.teacherGroupId() != null ? req.teacherGroupId() : req.studentGroupId();
        if (teacherGroupId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupa jest wymagana przy tworzeniu lekcji");
        }
        TeacherGroup teacherGroup = teacherGroupRepository.findByIdAndTeacherAndActiveTrue(teacherGroupId, teacher)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher group not found"));

        if (!Boolean.TRUE.equals(req.repeatWeekly())) {
            Schedule saved = scheduleRepository.save(buildSchedule(req, teacher, group, teacherGroup, req.date()));
            publishScheduleEvent(teacher, saved, "schedule.created", Map.of("recurring", false));
            return saved;
        }

        List<Instant> occurrenceDates = weeklyOccurrences(req);
        String recurrenceSeriesId = UUID.randomUUID().toString();
        int everyWeeks = req.repeatEveryWeeks() != null ? req.repeatEveryWeeks() : 1;
        List<Schedule> schedules = new ArrayList<>();
        for (Instant date : occurrenceDates) {
            schedules.add(buildSchedule(req, teacher, group, teacherGroup, date, recurrenceSeriesId, everyWeeks, req.repeatUntil()));
        }

        Schedule firstSaved = scheduleRepository.saveAll(schedules).iterator().next();
        publishScheduleEvent(teacher, firstSaved, "schedule.created", Map.of(
                "recurring", true,
                "occurrencesCount", schedules.size(),
                "recurrenceSeriesId", recurrenceSeriesId
        ));
        return firstSaved;
    }

    private Schedule buildSchedule(CreateScheduleRequest req,
                                   Teacher teacher,
                                   StudentGroup group,
                                   TeacherGroup teacherGroup,
                                   Instant date) {
        return buildSchedule(req, teacher, group, teacherGroup, date, null, null, null);
    }

    private Schedule buildSchedule(CreateScheduleRequest req,
                                   Teacher teacher,
                                   StudentGroup group,
                                   TeacherGroup teacherGroup,
                                   Instant date,
                                   String recurrenceSeriesId,
                                   Integer repeatEveryWeeks,
                                   Instant recurrenceEndsAt) {
        return Schedule.builder()
                .id(UUID.randomUUID().toString())
                .subject(req.subject())
                .date(date)
                .time(req.time())
                .room(req.room())
                .type(req.type())
                .color(req.color() != null ? req.color() : "primary")
                .teacherEntity(teacher)
                .studentGroup(group)
                .teacherGroup(teacherGroup)
                .recurrenceSeriesId(recurrenceSeriesId)
                .repeatEveryWeeks(repeatEveryWeeks)
                .recurrenceEndsAt(recurrenceEndsAt)
                .build();
    }

    private List<Instant> weeklyOccurrences(CreateScheduleRequest req) {
        if (req.date() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data pierwszych zajęć jest wymagana");
        }
        if (req.repeatUntil() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data końca powtarzania jest wymagana");
        }

        int everyWeeks = req.repeatEveryWeeks() != null ? req.repeatEveryWeeks() : 1;
        if (everyWeeks < 1 || everyWeeks > 52) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Odstęp powtarzania musi być od 1 do 52 tygodni");
        }

        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime first = req.date().atZone(zone);
        LocalDate endDate = LocalDate.ofInstant(req.repeatUntil(), zone);
        if (endDate.isBefore(first.toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data końca powtarzania nie może być wcześniejsza niż pierwsze zajęcia");
        }

        List<Instant> occurrences = new ArrayList<>();
        ZonedDateTime current = first;
        while (!current.toLocalDate().isAfter(endDate)) {
            occurrences.add(current.toInstant());
            if (occurrences.size() > 104) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seria zajęć może mieć maksymalnie 104 terminy");
            }
            current = current.plusWeeks(everyWeeks);
        }
        return occurrences;
    }

    @Transactional
    public Schedule updateSchedule(String id, CreateScheduleRequest req, String teacherUid) {
        Schedule schedule = scheduleRepository.findById(id)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));
        Teacher teacher = teacherRepository.findByClerkUserId(teacherUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Teacher not found"));
        if (schedule.getTeacherEntity() == null || !schedule.getTeacherEntity().getId().equals(teacher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nie możesz edytować cudzych zajęć");
        }

        // Full-replacement PUT semantics: null studentGroupId clears any existing group
        StudentGroup group = null;
        if (req.studentGroupId() != null) {
            group = studentGroupRepository.findById(req.studentGroupId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student group not found"));
        }
        Long teacherGroupId = req.teacherGroupId() != null ? req.teacherGroupId() : req.studentGroupId();
        if (teacherGroupId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupa jest wymagana przy lekcji");
        }
        TeacherGroup teacherGroup = teacherGroupRepository.findByIdAndTeacherAndActiveTrue(teacherGroupId, teacher)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher group not found"));

        schedule.setSubject(req.subject());
        schedule.setDate(req.date());
        schedule.setTime(req.time());
        schedule.setRoom(req.room());
        schedule.setType(req.type());
        schedule.setColor(req.color() != null ? req.color() : "primary");
        schedule.setStudentGroup(group);
        schedule.setTeacherGroup(teacherGroup);
        // teacherEntity is intentionally immutable after creation — not updated here
        // TODO: enforce ownership — only the creating teacher should be allowed to modify

        Schedule saved = scheduleRepository.save(schedule);
        publishScheduleEvent(teacher, saved, "schedule.updated", Map.of("recurring", isRecurring(saved)));
        return saved;
    }

    @Transactional
    public void deleteSchedule(String id, String teacherUid) {
        deleteSchedule(id, teacherUid, false);
    }

    @Transactional
    public void deleteSchedule(String id, String teacherUid, boolean deleteFutureOccurrences) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));
        Teacher teacher = teacherRepository.findByClerkUserId(teacherUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Teacher not found"));
        if (schedule.getTeacherEntity() == null || !schedule.getTeacherEntity().getId().equals(teacher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nie możesz usunąć cudzych zajęć");
        }
        if (schedule.isDeleted()) {
            return;
        }
        if (deleteFutureOccurrences && schedule.getRecurrenceSeriesId() != null && !schedule.getRecurrenceSeriesId().isBlank()) {
            List<Schedule> futureOccurrences = scheduleRepository
                    .findByRecurrenceSeriesIdAndTeacherEntityAndDateGreaterThanEqualAndDeletedFalseOrderByDateAscTimeAsc(
                            schedule.getRecurrenceSeriesId(),
                            teacher,
                            schedule.getDate()
                    );
            if (!futureOccurrences.isEmpty()) {
                Instant deletedAt = Instant.now();
                futureOccurrences.forEach(occurrence -> markDeleted(occurrence, deletedAt));
                scheduleRepository.saveAll(futureOccurrences);
                publishScheduleEvent(teacher, schedule, "schedule.deleted", Map.of(
                        "recurring", true,
                        "deleteFuture", true,
                        "deletedCount", futureOccurrences.size(),
                        "recurrenceSeriesId", schedule.getRecurrenceSeriesId()
                ));
                return;
            }
        }
        markDeleted(schedule, Instant.now());
        scheduleRepository.save(schedule);
        publishScheduleEvent(teacher, schedule, "schedule.deleted", Map.of(
                "recurring", isRecurring(schedule),
                "deleteFuture", false
        ));
    }

    private void markDeleted(Schedule schedule, Instant deletedAt) {
        schedule.setDeleted(true);
        schedule.setDeletedAt(deletedAt);
    }

    private boolean isRecurring(Schedule schedule) {
        return schedule != null
                && schedule.getRecurrenceSeriesId() != null
                && !schedule.getRecurrenceSeriesId().isBlank();
    }

    private void publishScheduleEvent(
            Teacher teacher,
            Schedule schedule,
            String eventName,
            Map<String, Object> extraPayload
    ) {
        if (teacher == null || teacher.getClerkUserId() == null || teacher.getClerkUserId().isBlank()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        if (schedule != null) {
            payload.put("scheduleId", schedule.getId());
            payload.put("date", schedule.getDate() != null ? schedule.getDate().toString() : null);
            payload.put("subject", schedule.getSubject());
            payload.put("teacherGroupId", schedule.getTeacherGroupId());
            payload.put("studentGroupId", schedule.getStudentGroup() != null ? schedule.getStudentGroup().getId() : null);
            payload.put("recurrenceSeriesId", schedule.getRecurrenceSeriesId());
        }
        payload.putAll(extraPayload);
        teacherRealtimeService.publishToTeacher(
                teacher.getClerkUserId(),
                eventName,
                TeacherRealtimeEvent.of(eventName, payload)
        );
    }
}
