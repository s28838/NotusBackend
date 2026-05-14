package com.notus.backend.grades;

import com.notus.backend.teachergroups.TeacherGroup;
import com.notus.backend.users.Student;
import com.notus.backend.users.Teacher;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "grades",
        indexes = {
                @Index(name = "idx_grades_student_id", columnList = "student_id"),
                @Index(name = "idx_grades_teacher_id", columnList = "teacher_id"),
                @Index(name = "idx_grades_group_id", columnList = "group_id"),
                @Index(name = "idx_grades_group_student", columnList = "group_id, student_id"),
                @Index(name = "idx_grades_source", columnList = "source_type, source_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class Grade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clerk_user_id", nullable = false)
    private String clerkUserId;

    @Column(nullable = false)
    private String subject;

    @Column(name = "grade_value", nullable = false)
    private String value;

    @Column(name = "issue_date", nullable = false)
    private LocalDateTime issueDate;

    @Column(name = "is_new", nullable = false)
    private boolean isNew = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private TeacherGroup group;

    @Column(name = "numeric_value", precision = 4, scale = 2)
    private BigDecimal numericValue;

    @Column(nullable = false)
    private Integer weight = 1;

    @Column(nullable = false)
    private String semester = "1";

    @Column(name = "source_type", nullable = false)
    private String sourceType = "MANUAL";

    @Column(name = "source_id")
    private Long sourceId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "grade_date")
    private LocalDate gradeDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
