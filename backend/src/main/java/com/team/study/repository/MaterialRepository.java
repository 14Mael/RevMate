package com.team.study.repository;

import com.team.study.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {
    List<Material> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Material> findByUserIdAndSubjectIdOrderByCreatedAtDesc(Long userId, Long subjectId);
    boolean existsByIdAndUserId(Long id, Long userId);
    boolean existsByIdAndUserIdAndSubjectId(Long id, Long userId, Long subjectId);
    boolean existsBySubjectIdAndUserId(Long subjectId, Long userId);

    @Query("select m.id from Material m where m.userId = :userId and m.subjectId = :subjectId")
    List<Long> findIdsByUserIdAndSubjectId(@Param("userId") Long userId, @Param("subjectId") Long subjectId);
}
