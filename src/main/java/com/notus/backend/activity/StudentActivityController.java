package com.notus.backend.activity;

import com.notus.backend.activity.dto.TeacherActivityResponse;
import com.notus.backend.activity.dto.TeacherNotificationsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/student")
public class StudentActivityController {

    private final StudentActivityService activityService;

    public StudentActivityController(StudentActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/notifications")
    public TeacherNotificationsResponse notifications(Principal principal) {
        return activityService.notifications(principal.getName());
    }

    @GetMapping("/activity")
    public TeacherActivityResponse activity(Principal principal) {
        return activityService.activity(principal.getName());
    }
}
