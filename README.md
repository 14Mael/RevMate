# RevMate · 复习资料智能学习助手

基于 **Spring AI（通义）** 的复习资料智能学习平台：上传自己的复习资料（文本/PDF/Word/图片，录音为加分项），系统将资料统一提取为文本并向量化入库，支持基于资料的 **RAG 问答（带原文出处）**、**联网问答兜底**，以及 **AI 自动出题**。

> 课程设计作业 · 4 人协作 · 详见 [设计文档](docs/设计文档.md)。

## 核心功能

1. 多用户登录（每人独立资料库）
2. 资料导入 + RAG 问答（答案带原文出处）
3. 联网问答兜底（自有资料答不上来时自动联网补充）
4. AI 出题（单选 / 填空 / 简答）

## 技术栈

- 后端：Spring Boot 3.x + JDK 21 + spring-ai-alibaba-starter-dashscope 1.0.0.1
- 模型：qwen-plus（对话/联网）、text-embedding-v3（向量）、qwen-vl（图片）、paraformer（语音，加分项）
- 数据：MySQL 8 + SimpleVectorStore（文件版向量库）
- 前端：Vue3 + Element Plus

## 目录结构

```
RevMate/
├── README.md          # 本文件：项目简介与快速开始
├── backend/           # Spring Boot 工程（待建）
├── frontend/          # Vue3 工程（待建）
└── docs/
    ├── 设计文档.md       # 完整设计文档
    └── plans/          # 各成员实现计划
        ├── 00-项目骨架与接口契约.md
        ├── 01-成员1-用户与资料管理.md
        ├── 02-成员2-RAG核心与提取器.md
        ├── 03-成员3-出题与Prompt工程.md
        └── 04-成员4-前端.md
```

## 团队分工

| 成员 | 模块 | 分支 |
|---|---|---|
| 1 | 用户与资料管理（后端） | `feat/auth` |
| 2 | RAG 核心与提取器（后端） | `feat/rag` |
| 3 | 出题与 Prompt 工程（后端） | `feat/quiz` |
| 4 | 前端（Vue3） | `feat/frontend` |

## 协作约定

- `main` 始终保持可运行，不直接在 main 上开发
- 每人在自己分支开发，通过 Pull Request 合并到 main
- 开工前先 `git pull origin main` 同步主干，小步提交
- 统一环境：JDK 21 / Maven（用 `mvnw`）/ Node 20 / MySQL 8，详见 [Plan 0](docs/plans/00-项目骨架与接口契约.md)
- API Key、数据库密码放本地 `application-local.yml`，**不提交**

## 快速开始

> 项目骨架待按 [Plan 0](docs/plans/00-项目骨架与接口契约.md) 搭建，搭好后在此补充启动步骤。
