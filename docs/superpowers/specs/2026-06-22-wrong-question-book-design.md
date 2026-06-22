# 错题本（Wrong Question Book）设计文档

日期：2026-06-22
状态：已与用户对齐，待实现

## 1. 背景与目标

当前 AI 出题流程为「配置 → 答题 → 回顾」三阶段（[QuizView.vue](../../../frontend/src/views/QuizView.vue)），
回顾页能区分对错（`gradingResults`），但答错的题在离开页面后即丢失，无法沉淀复习。

**目标**：把答错的题持久化收集成「错题本」，支持复习、重做、AI 智能巩固，按账户隔离。

## 2. 需求要点（已确认）

- **收录方式**：自动 + 手动。
  - 自动：提交批改后，答错的**填空题 / 选择题**自动收录。
  - 手动：回顾页对答对的题提供「加入错题本」按钮。
- **只收录 `single`（选择）和 `fill`（填空）两种题型**。`qa`（简答）因模糊比对、判对错不可靠，**不入库**。
- **组织方式**：按课程（subject / course）分组，页面顶部按课程筛选。
- **去重**：同一用户、同一课程、同一题干视为同一题，只保留一条；重复答错时 `wrongCount++`，更新 `wrongAnswer` 与 `lastWrongAt`。答错 ≥2 次时在回顾页 / 错题本提示「这道题你已经错了 N 次」。
- **功能档位（第 3 档，最完整）**：复习列表 + 重做错题 + AI 单题智能巩固。
- **智能巩固形态**：单题「举一反三」——针对某道错题让 AI 生成 2-3 道相似新题，当场自测，**不入库**。

## 3. 数据模型

新增 JPA 实体 `WrongQuestion`（沿用 [ChatHistory.java](../../../backend/src/main/java/com/team/study/entity/ChatHistory.java) 的账户隔离写法），表 `wrong_questions`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long，自增 | 主键 |
| `userId` | Long | 账户隔离，建索引 `idx_wrong_questions_user_id` |
| `subjectId` | Long | 所属课程 id |
| `course` | String(100) | 课程名（冗余，便于展示/分组） |
| `type` | String | `single` / `fill` |
| `stem` | TEXT | 题干 |
| `optionsJson` | TEXT | 选择题选项序列化 JSON；填空为 null |
| `answer` | String | 正确答案 |
| `analysis` | TEXT | 解析 |
| `wrongAnswer` | String | 最近一次错误答案 |
| `wrongCount` | Int | 答错次数，默认 1 |
| `mastered` | Boolean | 是否已掌握，默认 false |
| `createdAt` | DateTime | 首次收录时间 |
| `lastWrongAt` | DateTime | 最近答错时间 |

**去重键**：`(userId, subjectId, stem)` 唯一约束。Service 层 upsert：存在则 `wrongCount++` 并更新 `wrongAnswer`/`lastWrongAt`，不存在则插入。手动收藏答对题时 `wrongAnswer` 置空、`wrongCount` 不自增（或按收藏语义不计错次，见实现备注）。

## 4. 后端 API

新增 `WrongQuestionController`（`/api/wrong-questions`）+ Service + Repository，全部经 `SecurityUtil.getCurrentUserId()` 隔离。

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/wrong-questions/batch` | 批量收录。提交批改后自动调，仅传答错的 `single`/`fill` 题。返回每题收录后的 `wrongCount`，前端据此提示「错了 N 次」。 |
| `POST` | `/api/wrong-questions` | 单题手动收藏（回顾页「加入错题本」）。 |
| `GET` | `/api/wrong-questions` | 拉取当前用户全部错题（前端按 `course` 分组 + 题型筛选）。 |
| `PATCH` | `/api/wrong-questions/{id}/master` | 标记已掌握（重做答对时调）。 |
| `DELETE` | `/api/wrong-questions/{id}` | 删除某条。 |
| `POST` | `/api/wrong-questions/{id}/reinforce` | 举一反三：基于该题让 AI 生成 2-3 道相似题，返回 `QuestionItem[]`，**不入库**。 |

- 去重 upsert 逻辑集中在 Service，`batch` 与单题收藏共用同一方法。
- `reinforce` 复用现有出题的 LLM 客户端（见 [QuizServiceImpl.java](../../../backend/src/main/java/com/team/study/service/QuizServiceImpl.java)），prompt 给定原题，要求模型仿写**同题型**相似题。
- `master` / `delete` / `reinforce` 必须校验该条 `WrongQuestion.userId == 当前用户`，否则拒绝（越权防护）。

## 5. 前端

### 5.1 导航与路由
- 侧边栏 [AppLayout.vue](../../../frontend/src/layouts/AppLayout.vue) 新增入口「错题本」（与「我的资料」「AI 出题」同级）。
- 新路由 `/wrong-questions` → 新页面 `WrongBookView.vue`。

### 5.2 组件抽取（决策 A）
将 `QuizView.vue` 中「答题 + 回顾」两阶段逻辑抽成可复用组件 **`QuizRunner.vue`**：
- 输入：题目列表 `Question[]` + 批改/收尾回调。
- 出题页与错题本「重做错题」共用同一组件，避免两份重复 UI。
- 抽取后 `QuizView.vue` 仅保留「配置」阶段与对 `QuizRunner` 的编排。

### 5.3 出题页改动（`QuizView.vue`）
- `handleSubmit` 批改后，筛出答错的 `single`/`fill` 题，调 `POST /batch` 自动收录。
- 自动收录失败**不阻断**批改：静默记录日志，回顾页照常显示。
- 回顾卡：错题显示「已加入错题本」，`wrongCount≥2` 时显示「这道题你已经错了 N 次」；答对的题显示「加入错题本」按钮。

### 5.4 错题本页面（`WrongBookView.vue`）
- 顶部按课程 select 筛选（与出题页一致），并可按题型（填空/选择）筛选。
- 错题卡列表：题干、选项、你的错误答案、正确答案、解析、错误次数标记。
- 每卡操作：`删除`、`已掌握`、`举一反三`。
  - 「举一反三」：内联调 `reinforce`，展示 AI 生成的 2-3 道相似题，可当场作答自测（轻量，不走完整流程、不入库）。按钮 loading，失败给 toast 可重试。
- 顶部「重做错题」按钮：把当前筛选后的**未掌握**错题塞进 `QuizRunner`，答对的题在批改后自动调 `master` 标记已掌握。
  - 若筛选后无未掌握错题：按钮置灰，提示「这门课没有待复习的错题」。

### 5.5 API 层
- 新增 `src/api/wrongQuestion.ts` 封装上述接口。
- `src/api/types.ts` 增加 `WrongQuestion` 类型。

## 6. 错误处理与边界
- 自动收录失败：静默 + 日志，不挡答题反馈。
- `reinforce` 慢/失败：loading + toast + 可重试。
- 重做无待复习错题：按钮置灰并提示。
- 去重并发：唯一约束 + Service 层捕获 `DataIntegrityViolation`，避免重复插入。

## 7. 测试
- 后端（沿用 [QuizServiceImplTest.java](../../../backend/src/test/java/com/team/study/service/QuizServiceImplTest.java) 风格）：
  - upsert 去重：首次插入；重复时 `wrongCount++` 且更新 `wrongAnswer`/`lastWrongAt`。
  - 账户隔离：`GET` 只返回当前用户错题。
  - 越权防护：`master`/`delete`/`reinforce` 操作他人错题被拒。
- 前端：轻量，以手动验证为主。

## 8. 非目标（YAGNI）
- 不收录简答题。
- 智能巩固不做「整组成卷」，仅单题举一反三。
- 不做错题导出 / 分享 / 统计图表。
