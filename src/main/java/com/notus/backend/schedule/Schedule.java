package com.notus.backend.schedule;

import com.notus.backend.attendance.group.StudentGroup;
import com.notus.backend.teachergroups.TeacherGroup;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {
    @Id
    private String id;

    private Instant date;
    private String time;
    private String subject;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id")
    private com.notus.backend.users.Teacher teacherEntity;

    @Transient
    public String getTeacher() {
        return teacherEntity != null ? teacherEntity.getName() : null;
    }

    private String type;
    private String room;
    private String color;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_group_id")
    private StudentGroup studentGroup;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_group_id")
    private TeacherGroup teacherGroup;

    private String recurrenceSeriesId;
    private Integer repeatEveryWeeks;
    private Instant recurrenceEndsAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted;
    private Instant deletedAt;

    @Transient
    public String getStudentGroupName() {
        if (teacherGroup != null) {
            return teacherGroup.getName();
        }
        return studentGroup != null ? studentGroup.getCode() : null;
    }

    @Transient
    public Long getTeacherGroupId() {
        return teacherGroup != null ? teacherGroup.getId() : null;
    }
}
