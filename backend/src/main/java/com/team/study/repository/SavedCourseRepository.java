package com.team.study.repository;

import com.team.study.entity.SavedCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedCourseRepository extends JpaRepository<SavedCourse, Long> {

    List<SavedCourse> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SavedCourse> findByIdAndUserId(Long id, Long userId);
}
