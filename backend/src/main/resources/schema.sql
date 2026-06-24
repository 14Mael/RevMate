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

-- 聊天历史表：按用户隔离，messages_json 整段会话消息序列化为 JSON
CREATE TABLE IF NOT EXISTS chat_histories (
    id VARCHAR(64) PRIMARY KEY COMMENT '前端生成的会话 id',
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    subject_id BIGINT COMMENT '会话关联的学科',
    material_id BIGINT COMMENT '单资料问答关联的资料；智能问答为空',
    course VARCHAR(100) COMMENT '学科名快照',
    messages_json LONGTEXT COMMENT 'JSON 序列化的消息列表',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近活跃时间，每次保存刷新',
    INDEX idx_chat_histories_user_id (user_id),
    INDEX idx_chat_histories_material_id (material_id)
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

-- 推荐课程收藏表：推荐结果不持久化，只有用户主动收藏的课程入库
CREATE TABLE IF NOT EXISTS saved_courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    platform VARCHAR(100),
    url VARCHAR(1024) NOT NULL,
    reason VARCHAR(1000),
    difficulty VARCHAR(20),
    subject_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_saved_courses_user_id (user_id),
    INDEX idx_saved_courses_subject_id (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 错题本表：按用户和课程隔离，同一题干只保留一条
CREATE TABLE IF NOT EXISTS wrong_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    course VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'single/fill',
    stem LONGTEXT NOT NULL,
    stem_hash CHAR(64) NOT NULL COMMENT '题干 SHA-256，用于唯一约束',
    options_json LONGTEXT COMMENT '选择题选项 JSON；填空为空',
    answer VARCHAR(1000) NOT NULL,
    analysis LONGTEXT,
    wrong_answer VARCHAR(1000) COMMENT '最近一次错误答案',
    wrong_count INT NOT NULL DEFAULT 1,
    mastered TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_wrong_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wrong_questions_user_id (user_id),
    INDEX idx_wrong_questions_subject_id (subject_id),
    UNIQUE KEY uk_wrong_questions_user_subject_stem (user_id, subject_id, stem_hash),
    CONSTRAINT fk_wrong_questions_subject
        FOREIGN KEY (subject_id) REFERENCES subjects(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
