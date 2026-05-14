package com.notus.backend.teachergroups;

import com.notus.backend.grades.GradeService;
import com.notus.backend.grades.dto.CreateGradeRequest;
import com.notus.backend.grades.dto.DeleteGradeResponse;
import com.notus.backend.grades.dto.GradeResponse;
import com.notus.backend.grades.dto.UpdateGradeRequest;
import com.notus.backend.teachergroups.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/teacher/groups")
public class TeacherGroupController {

    private final TeacherGroupService groupService;
    private final GroupInvitationService invitationService;
    private final GroupMembershipService membershipService;
    private final TeacherStudentSummaryService summaryService;
    private final GradeService gradeService;

    public TeacherGroupController(TeacherGroupService groupService,
                                  GroupInvitationService invitationService,
                                  GroupMembershipService membershipService,
                                  TeacherStudentSummaryService summaryService,
                                  GradeService gradeService) {
        this.groupService = groupService;
        this.invitationService = invitationService;
        this.membershipService = membershipService;
        this.summaryService = summaryService;
        this.gradeService = gradeService;
    }

    @GetMapping
    public List<TeacherGroupResponse> list(Principal principal) {
        return groupService.listGroups(principal.getName());
    }

    @GetMapping("/{groupId}")
    public TeacherGroupDetailsResponse details(Principal principal, @PathVariable Long groupId) {
        return groupService.getDetails(principal.getName(), groupId);
    }

    @PostMapping
    public TeacherGroupResponse create(Principal principal, @Valid @RequestBody CreateTeacherGroupRequest request) {
        return groupService.create(principal.getName(), request);
    }

    @PutMapping("/{groupId}")
    public TeacherGroupResponse update(Principal principal,
                                       @PathVariable Long groupId,
                                       @Valid @RequestBody UpdateTeacherGroupRequest request) {
        return groupService.update(principal.getName(), groupId, request);
    }

    @DeleteMapping("/{groupId}")
    public void delete(Principal principal, @PathVariable Long groupId) {
        groupService.delete(principal.getName(), groupId);
    }

    @GetMapping("/{groupId}/students")
    public List<GroupStudentTableRowResponse> students(Principal principal, @PathVariable Long groupId) {
        return summaryService.listStudents(principal.getName(), groupId);
    }

    @PostMapping("/{groupId}/students/invite")
    public InviteStudentResponse invite(Principal principal,
                                        @PathVariable Long groupId,
                                        @RequestBody InviteStudentRequest request) {
        return invitationService.invite(principal.getName(), groupId, request);
    }

    @PutMapping("/{groupId}/students/{studentId}")
    public UpdateGroupStudentResponse updateStudent(Principal principal,
                                                    @PathVariable Long groupId,
                                                    @PathVariable Long studentId,
                                                    @Valid @RequestBody UpdateGroupStudentRequest request) {
        return membershipService.updateStudent(principal.getName(), groupId, studentId, request);
    }

    @DeleteMapping("/{groupId}/students/{studentId}")
    public RemoveGroupStudentResponse removeStudent(Principal principal,
                                                    @PathVariable Long groupId,
                                                    @PathVariable Long studentId) {
        return membershipService.removeStudent(principal.getName(), groupId, studentId);
    }

    @GetMapping("/{groupId}/students/{studentId}/attendance")
    public StudentAttendanceTableResponse attendance(Principal principal,
                                                     @PathVariable Long groupId,
                                                     @PathVariable Long studentId) {
        return summaryService.attendance(principal.getName(), groupId, studentId);
    }

    @GetMapping("/{groupId}/students/{studentId}/grades")
    public StudentGradesBySemesterResponse grades(Principal principal,
                                                  @PathVariable Long groupId,
                                                  @PathVariable Long studentId) {
        return summaryService.grades(principal.getName(), groupId, studentId);
    }

    @PostMapping("/{groupId}/students/{studentId}/grades")
    public GradeResponse createGrade(Principal principal,
                                     @PathVariable Long groupId,
                                     @PathVariable Long studentId,
                                     @RequestBody CreateGradeRequest request) {
        return gradeService.createManualGrade(principal.getName(), groupId, studentId, request);
    }

    @PutMapping("/{groupId}/students/{studentId}/grades/{gradeId}")
    public GradeResponse updateGrade(Principal principal,
                                     @PathVariable Long groupId,
                                     @PathVariable Long studentId,
                                     @PathVariable Long gradeId,
                                     @RequestBody UpdateGradeRequest request) {
        return gradeService.updateGrade(principal.getName(), groupId, studentId, gradeId, request);
    }

    @DeleteMapping("/{groupId}/students/{studentId}/grades/{gradeId}")
    public DeleteGradeResponse deleteGrade(Principal principal,
                                           @PathVariable Long groupId,
                                           @PathVariable Long studentId,
                                           @PathVariable Long gradeId) {
        return gradeService.deleteGrade(principal.getName(), groupId, studentId, gradeId);
    }
}
