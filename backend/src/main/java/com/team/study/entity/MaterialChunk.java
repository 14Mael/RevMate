package com.team.study.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "material_chunks",
        indexes = {
                @Index(name = "idx_material_chunks_user_id", columnList = "user_id"),
                @Index(name = "idx_material_chunks_material_id", columnList = "material_id")
        }
)
public class MaterialChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false, length = 512)
    private String source;

    @Column(nullable = false, length = 4000)
    private String text;

    @Lob
    @Column(name = "embedding", columnDefinition = "LONGTEXT")
    private String embedding;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", nullable = false, length = 20)
    private EmbeddingStatus embeddingStatus = EmbeddingStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
