package com.notus.backend.schedule;

import com.notus.backend.users.Role;
import com.notus.backend.users.UserDto;
import com.notus.backend.users.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final UserService userService;

    @GetMapping("/today")
    public List<ScheduleResponse> getTodaySchedule(
            Authentication auth,
            HttpServletRequest request,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String teacherName,
            @RequestParam(required = false) Long groupId
    ) {
        UserDto user = currentUser(auth, request);
        if (user.role() == Role.STUDENT) {
            com.notus.backend.users.Student student = userService.findStudentWithGroupsByUid((String) auth.getPrincipal()).orElse(null);
            return toResponse(scheduleService.getTodayScheduleForStudent(student));
        }
        if (teacherId == null && groupId == null && (teacherName == null || teacherName.isBlank())) {
            teacherId = user.id();
        }
        if (teacherId != null && !teacherId.equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nie możesz pobrać planu innego nauczyciela.");
        }
        scheduleService.assertTeacherGroupOwned(groupId, user.id());
        return toResponse(scheduleService.getTodaySchedule(teacherId, teacherName, groupId));
    }

    @GetMapping("/teacher/today")
    public List<ScheduleResponse> getTeacherTodaySchedule(
            Authentication auth,
            HttpServletRequest request
    ) {
        String uid = (String) auth.getPrincipal();
        String email = (String) request.getAttribute("clerk_email");
        String name = (String) request.getAttribute("clerk_name");

        UserDto user = userService.findOrCreate(uid, email, name);

        if (user.role() != Role.TEACHER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tylko nauczyciel może pobrać swój plan"
            );
        }

        return toResponse(scheduleService.getTodaySchedule(user.id(), null, null));
    }

    @GetMapping("/by-day")
    public List<ScheduleResponse> getScheduleByDay(Authentication auth,
                                                   HttpServletRequest request,
                                                   @RequestParam String date) {
        return getScheduleByDayPath(auth, request, date);
    }

    @GetMapping("/day/{date}")
    public List<ScheduleResponse> getScheduleByDayPath(
            Authentication auth,
            HttpServletRequest request,
            @PathVariable String date
    ) {
        String uid = (String) auth.getPrincipal();
        String email = (String) request.getAttribute("clerk_email");
        String name = (String) request.getAttribute("clerk_name");
        UserDto user = userService.findOrCreate(uid, email, name);

        java.time.Instant start = java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        java.time.Instant end = java.time.LocalDate.parse(date).plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();

        if (user.role() == Role.STUDENT) {
            com.notus.backend.users.Student student = userService.findStudentWithGroupsByUid(uid).orElse(null);
            return toResponse(scheduleService.getScheduleForStudentInRange(student, start, end));
        } else {
            return toResponse(scheduleService.getSchedule(start, end, user.id(), null, null));
        }
    }

    @GetMapping("/next")
    public ScheduleResponse getNextSchedule(
            Authentication auth,
            HttpServletRequest request
    ) {
        String uid = (String) auth.getPrincipal();
        String email = (String) request.getAttribute("clerk_email");
        String name = (String) request.getAttribute("clerk_name");
        UserDto user = userService.findOrCreate(uid, email, name);

        if (user.role() == Role.STUDENT) {
            com.notus.backend.users.Student student = userService.findStudentWithGroupsByUid(uid).orElse(null);
            return ScheduleResponse.from(scheduleService.getNextSchedule(null, null, null, student));
        } else {
            return ScheduleResponse.from(scheduleService.getNextSchedule(user.id(), null, null, null));
        }
    }

    @GetMapping
    public List<ScheduleResponse> getSchedule(
            Authentication auth,
            HttpServletRequest request,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String teacherName,
            @RequestParam(required = false) Long groupId
    ) {
        UserDto user = currentUser(auth, request);
        if (user.role() == Role.STUDENT) {
            com.notus.backend.users.Student student = userService.findStudentWithGroupsByUid((String) auth.getPrincipal()).orElse(null);
            return toResponse(scheduleService.getScheduleForStudentInRange(student, Instant.parse(start), Instant.parse(end)));
        }
        if (teacherId == null && groupId == null && (teacherName == null || teacherName.isBlank())) {
            teacherId = user.id();
        }
        if (teacherId != null && !teacherId.equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nie możesz pobrać planu innego nauczyciela.");
        }
        scheduleService.assertTeacherGroupOwned(groupId, user.id());
        return toResponse(scheduleService.getSchedule(
                Instant.parse(start),
                Instant.parse(end),
                teacherId,
                teacherName,
                groupId
        ));
    }

    @GetMapping("/{id}")
    public ScheduleResponse getById(@PathVariable String id) {
        return ScheduleResponse.from(scheduleService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(
            Authentication auth,
            HttpServletRequest request,
            @RequestBody CreateScheduleRequest req
    ) {
        String uid = (String) auth.getPrincipal();
        String email = (String) request.getAttribute("clerk_email");
        String name = (String) request.getAttribute("clerk_name");
        UserDto user = userService.findOrCreate(uid, email, name);
        if (user.role() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers can create schedule entries");
        }
        return ScheduleResponse.from(scheduleService.createSchedule(req, uid));
    }

    @PutMapping("/{id}")
    public ScheduleResponse update(
            Authentication auth,
            HttpServletRequest request,
            @PathVariable String id,
            @RequestBody CreateScheduleRequest req
    ) {
        String uid = (String) auth.getPrincipal();
        String email = (String) request.getAttribute("clerk_email");
        String name = (String) request.getAttribute("clerk_name");
        UserDto user = userService.findOrCreate(uid, email, name);
        if (user.role() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers can update schedule entries");
        }
        return ScheduleResponse.from(scheduleService.updateSchedule(id, req, uid));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            Authentication auth,
            HttpServletRequest request,
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean deleteFuture
    ) {
        String uid = (String) auth.getPrincipal();
        String email = (String) request.getAttribute("clerk_email");
        String name = (String) request.getAttribute("clerk_name");
        UserDto user = userService.findOrCreate(uid, email, name);
        if (user.role() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers can delete schedule entries");
        }
        scheduleService.deleteSchedule(id, uid, deleteFuture);
    }

    private List<ScheduleResponse> toResponse(List<Schedule> schedules) {
        return schedules.stream().map(ScheduleResponse::from).toList();
    }

    private UserDto currentUser(Authentication auth, HttpServletRequest request) {
        String uid = (String) auth.getPrincipal();
        String email = (String) request.getAttribute("clerk_email");
        String name = (String) request.getAttribute("clerk_name");
        return userService.findOrCreate(uid, email, name);
    }
}
