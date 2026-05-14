package com.notus.backend.teachergroups;

import com.notus.backend.users.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherGroupRepository extends JpaRepository<TeacherGroup, Long> {
    List<TeacherGroup> findByTeacherAndActiveTrueOrderByCreatedAtDesc(Teacher teacher);
    Optional<TeacherGroup> findByIdAndTeacherAndActiveTrue(Long id, Teacher teacher);
    List<TeacherGroup> findByTeacherAndSubjectIgnoreCaseAndActiveTrue(Teacher teacher, String subject);
}
