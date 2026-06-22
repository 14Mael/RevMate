package com.team.study.repository;

import com.team.study.entity.WrongQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WrongQuestionRepository extends JpaRepository<WrongQuestion, Long> {
    List<WrongQuestion> findByUserIdOrderByLastWrongAtDesc(Long userId);

    Optional<WrongQuestion> findByUserIdAndSubjectIdAndStem(Long userId, Long subjectId, String stem);

    Optional<WrongQuestion> findByUserIdAndSubjectIdAndStemHash(Long userId, Long subjectId, String stemHash);

    Optional<WrongQuestion> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
