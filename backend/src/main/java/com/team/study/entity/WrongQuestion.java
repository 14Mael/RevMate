package com.team.study.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 错题本条目（按用户隔离）。
 */
@Data
@NoArgsConstructor
@Entity
@Table(
        name = "wrong_questions",
        indexes = {
                @Index(name = "idx_wrong_questions_user_id", columnList = "user_id"),
                @Index(name = "idx_wrong_questions_subject_id", columnList = "subject_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_wrong_questions_user_subject_stem", columnNames = {
                        "user_id", "subject_id", "stem_hash"
                })
        }
)
public class WrongQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(nullable = false, length = 100)
    private String course;

    @Column(nullable = false, length = 20)
    private String type;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String stem;

    @Column(name = "stem_hash", nullable = false, length = 64)
    private String stemHash;

    @Lob
    @Column(name = "options_json", columnDefinition = "LONGTEXT")
    private String optionsJson;

    @Column(nullable = false, length = 1000)
    private String answer;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String analysis;

    @Column(name = "wrong_answer", length = 1000)
    private String wrongAnswer;

    @Column(name = "wrong_count", nullable = false)
    private Integer wrongCount;

    @Column(nullable = false)
    private Boolean mastered;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_wrong_at", nullable = false)
    private LocalDateTime lastWrongAt;
}
