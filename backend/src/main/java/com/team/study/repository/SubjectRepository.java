package com.team.study.repository;

import com.team.study.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    boolean existsByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndName(Long userId, String name);
    Optional<Subject> findByUserIdAndName(Long userId, String name);
    List<Subject> findByUserIdOrderByCreatedAtDesc(Long userId);
}
