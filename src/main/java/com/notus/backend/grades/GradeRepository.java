package com.notus.backend.grades;

import com.notus.backend.teachergroups.TeacherGroup;
import com.notus.backend.users.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findByClerkUserIdOrderByIssueDateDesc(String clerkUserId);
    List<Grade> findByGroupAndStudentAndDeletedAtIsNullOrderByGradeDateDesc(TeacherGroup group, Student student);
    Optional<Grade> findByIdAndGroupAndStudentAndDeletedAtIsNull(Long id, TeacherGroup group, Student student);
    Optional<Grade> findByStudentAndGroupAndSourceTypeAndSourceIdAndDeletedAtIsNull(Student student, TeacherGroup group, String sourceType, Long sourceId);
}
