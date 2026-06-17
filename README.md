# 复习资料智能学习助手 — 设计文档
## 1. 项目概述

一个基于 Spring AI 的复习资料智能学习平台。用户登录后上传自己的复习资料，系统将资料切片并向量化入库；用户可基于自己的资料进行问答（答案带原文出处），当自有资料无法回答时自动联网搜索补充作答；并可让 AI 根据资料自动生成练习题。

### 核心闭环

```
登录 → 上传复习资料(PDF/Word/txt) → 解析切片 + 向量化入库(按用户隔离)
     → 提问：先检索自己的资料
              ├─ 命中 → 基于资料回答(带原文出处)
              └─ 资料不足 → 联网搜索补充回答(带网页来源)
     → 选一份资料/主题 → AI 自动生成练习题(选择/填空/简答)
```

### 四个功能点

1. 多用户登录（小范围，每人独立资料库）
2. 资料导入 + RAG 问答
3. 联网问答兜底（自有资料答不上来时联网补充）
4. AI 出题

### 可选项（YAGNI，列为加分项）

- 自动批改 / 错题本
- 复杂权限、角色管理
- 高并发优化、复杂运维部署
- 搜索并导入外部资料（本期只做"联网问答"，不做"联网搜资料入库"）

---

## 2. 后端架构（分层）

- **Controller 层**
    - `AuthController` — 注册 / 登录
    - `MaterialController` — 上传 / 列表 / 删除
    - `ChatController` — 问答
    - `QuizController` — 出题
- **Service 层**
    - `AuthService` — 用户认证
    - `MaterialService` — 解析 → 切片 → embedding → 入库
    - `RagService` — 检索 + 问答 + 联网兜底
    - `QuizService` — 出题
- **AI 集成**：Spring AI 的 `ChatClient` + `EmbeddingModel`（通义）+ `VectorStore`
- **持久层**：MySQL 存 `users`、`materials`；向量先用文件版 `SimpleVectorStore`（零额外部署）
- **安全**：Spring Security + JWT（简单版）

### 用户隔离机制

每个文本切片入库时携带 metadata `{userId, materialId, source}`；检索时按 `userId` 过滤，保证每个用户只能检索到自己的资料。

---

## 3. 数据模型

### users

| 字段 | 说明 |
|---|---|
| id | 主键 |
| username | 用户名（唯一） |
| password | 加密存储（BCrypt） |
| created_at | 创建时间 |

### materials

| 字段 | 说明 |
|---|---|
| id | 主键 |
| user_id | 所属用户 |
| filename | 原始文件名 |
| type | 文件类型（pdf/word/txt） |
| status | 处理状态（已入库/处理中/失败） |
| created_at | 创建时间 |

### 向量库 chunk

- 内容：切片文本
- metadata：`{ userId, materialId, source(文件名/页码) }` — 用于用户隔离与回答溯源

---

## 4. 关键数据流

### 4.1 资料上传与入库

1. 用户上传文件 → `MaterialController`
2. `MaterialService` 用 Apache Tika 解析出纯文本
3. 文本切片（按长度 + 重叠）
4. 每个切片调用 `EmbeddingModel` 生成向量
5. 带 metadata 写入 `VectorStore`
6. `materials` 表记录元信息与状态

### 4.2 RAG 问答（含联网兜底）

1. 用户提问 → `ChatController` → `RagService`
2. 对问题做 embedding，在 `VectorStore` 中按 `userId` 过滤检索 Top-K 切片
3. 若检索到足够相关切片：组装 prompt（资料片段 + 问题）→ `ChatClient` 生成回答，附带原文出处
4. 若资料不足（检索为空或相关度低）：调用通义 `enable_search` 联网作答，回答附带网页来源
5. 返回答案 + 来源列表

### 4.3 AI 出题

1. 用户选一份资料 / 主题 → `QuizController` → `QuizService`
2. 取该资料的代表性切片作为上下文
3. 用结构化 prompt 让 LLM 输出 JSON 格式题目（选择 / 填空 / 简答）
4. 返回题目列表

---

## 5. 四人分工

| 成员 | 模块 | 主要工作 | 主要交付 |
|---|---|---|---|
| **1** | 用户与资料管理（后端） | Spring Security + JWT 登录注册、文件上传、MySQL、materials 增删查 | `AuthController/Service`、`MaterialController`、users/materials 表 |
| **2** | RAG 核心（后端） | Tika 解析 → 切片 → embedding → 入向量库；检索；`ChatClient` 问答；联网兜底 | `MaterialService`、`RagService`、`ChatController` |
| **3** | 出题 + Prompt 工程（后端） | `QuizService`、prompt 模板、LLM 输出结构化 JSON 题目；（加分项：资料总结） | `QuizController/Service`、prompt 模板 |
| **4** | 前端（Vue3 + Element Plus） | 登录页、资料上传/列表页、问答页(显示出处)、出题/答题页 | 前端工程 |

接口先用 Apidog/Postman 约定接口契约，前后端并行开发。

---

## 6. 技术栈

- **后端框架**：Spring Boot 3.x
- **AI 框架**：spring-ai-alibaba（通义）
- **模型**：`qwen-plus`（对话，开 `enable_search` 联网）+ `text-embedding-v3`（向量）
- **数据库**：MySQL
- **文档解析**：Apache Tika
- **鉴权**：JWT（jjwt）
- **向量库**：SimpleVectorStore（文件版，零部署）；有余力可换 PGVector / Redis
- **前端**：Vue3 + Element Plus + axios

---

## 7. 测试策略

- 各 Service 单元测试（AI 调用使用 mock）
- 接口用 Apidog/Postman 联调
- 关键 RAG 流程做一个集成测试：上传 → 提问 → 得到带出处的回答

---

## 8. 里程碑建议（1~2 周）

- **第 1~3 天**：搭脚手架、约定接口、建库建表、跑通登录 + 上传
- **第 4~7 天**：RAG 问答闭环（解析/切片/向量/检索/回答）+ 前端问答页
- **第 8~10 天**：联网兜底 + AI 出题 + 前端出题页
- **第 11~14 天**：联调、修 bug、准备演示、（有余力做加分项）
