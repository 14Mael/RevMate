package com.team.study.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天历史（按用户隔离）
 * - id 由前端生成的字符串作主键，保留「同一会话按 id 更新」语义
 * - messagesJson 整段消息序列化为 JSON 存 LONGTEXT，会话整体读写，无需拆表
 */
@Data
@NoArgsConstructor
@Entity
@Table(
        name = "chat_histories",
        indexes = {
                @Index(name = "idx_chat_histories_user_id", columnList = "user_id")
        }
)
public class ChatHistory {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(length = 100)
    private String course;

    @Lob
    @Column(name = "messages_json", columnDefinition = "LONGTEXT")
    private String messagesJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
