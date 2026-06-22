package com.team.study.repository;

import com.team.study.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, String> {
    List<ChatHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ChatHistory> findByIdAndUserId(String id, Long userId);

    void deleteByIdAndUserId(String id, Long userId);

    void deleteByUserId(Long userId);
}
