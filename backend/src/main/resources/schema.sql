-- RevMate 数据库建表脚本
-- 使用方式：mysql -u root -p < schema.sql

CREATE DATABASE IF NOT EXISTS revmate
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE revmate;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学科表
CREATE TABLE IF NOT EXISTS subjects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_subjects_user_id (user_id),
    UNIQUE KEY uk_subject_user_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 资料表
CREATE TABLE IF NOT EXISTS materials (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    subject_id BIGINT COMMENT '所属学科，旧资料可为空，新上传必须传',
    filename VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'txt/pdf/word/image/audio',
    storage_path VARCHAR(512) COMMENT '本地实际存储路径，内部使用',
    preview_path VARCHAR(512) COMMENT '预览 PDF 路径，PDF 类型为原文件路径',
    preview_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PROCESSING/READY/FAILED',
    preview_message VARCHAR(255) COMMENT '预览状态说明或失败原因',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/READY/FAILED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_materials_subject_id (subject_id),
    CONSTRAINT fk_materials_subject
        FOREIGN KEY (subject_id) REFERENCES subjects(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 资料切片表：持久化 RAG/出题上下文，避免服务重启后 READY 资料无法检索
CREATE TABLE IF NOT EXISTS material_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    source VARCHAR(512) NOT NULL,
    text VARCHAR(4000) NOT NULL,
    embedding LONGTEXT COMMENT 'JSON 序列化后的 float embedding',
    embedding_model VARCHAR(100) COMMENT '生成 embedding 的模型名称',
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/READY/FAILED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_material_chunks_user_id (user_id),
    INDEX idx_material_chunks_material_id (material_id),
    CONSTRAINT fk_material_chunks_material
        FOREIGN KEY (material_id) REFERENCES materials(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
