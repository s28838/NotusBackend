package com.notus.backend.teachergroups;

import com.notus.backend.teachergroups.dto.StudentGroupResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/student/groups")
public class StudentTeacherGroupController {

    private final GroupMembershipService membershipService;
    private final TeacherStudentSummaryService summaryService;

    public StudentTeacherGroupController(GroupMembershipService membershipService,
                                         TeacherStudentSummaryService summaryService) {
        this.membershipService = membershipService;
        this.summaryService = summaryService;
    }

    @GetMapping
    public List<StudentGroupResponse> list(Principal principal) {
        return membershipService.listStudentGroups(principal.getName());
    }

    @GetMapping("/{groupId}/grades")
    public com.notus.backend.teachergroups.dto.StudentGradesBySemesterResponse grades(Principal principal,
                                                                                      @org.springframework.web.bind.annotation.PathVariable Long groupId) {
        return summaryService.studentGrades(principal.getName(), groupId);
    }
}
