package com.team.study.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "materials")
public class Material {

    public enum Status {
        PROCESSING, READY, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false, length = 20)
    private String type;        // txt/pdf/word/image/audio

    /** 文件在本地的实际存储路径，仅后端内部使用，不对外暴露 */
    @Column(name = "storage_path", length = 512)
    private String storagePath;

    /** 预览用 PDF 的本地路径;pdf 类型为原文件路径,不支持预览或转换失败为 null */
    @Column(name = "preview_path", length = 512)
    private String previewPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = Status.PROCESSING;
        }
    }
}
