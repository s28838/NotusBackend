package com.notus.backend.quiz;

import com.notus.backend.teachergroups.TeacherGroup;
import com.notus.backend.users.Teacher;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(nullable = false, columnDefinition = "integer default 1")
    private int version = 1;

    @Column(name = "parent_quiz_id")
    private Long parentQuizId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private TeacherGroup group;

    @Column(name = "count_as_grade", nullable = false, columnDefinition = "boolean default false")
    private boolean countAsGrade = false;

    @Column(name = "grade_weight")
    private Integer gradeWeight;

    @Column(name = "grade_semester")
    private String gradeSemester;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean archived = false;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuizQuestion> questions = new ArrayList<>();

    public void addQuestion(QuizQuestion question) {
        questions.add(question);
        question.setQuiz(this);
    }
}
