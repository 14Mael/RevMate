# 学科资料作用域设计

- 日期：2026-06-19
- 功能：按学科整理资料，并让 AI 问答与出题基于指定学科
- 选定方案：方案 1，新增真实 `subjects` 表，资料通过外键归属到学科

## 背景

当前 RevMate 的资料只按用户隔离：

- 资料表 `materials` 通过 `user_id` 归属用户。
- 问答接口按当前用户的全部资料切片检索。
- 出题接口要求 `materialId`，只能基于单份资料出题。

用户希望先创建一个学科，例如“Java”，再在这个学科下上传多份资料。后续 AI 问答和 AI 出题都应基于这个学科整体，而不是混用用户的全部资料。

## 目标

1. 用户可以创建、查看、删除自己的学科。
2. 每份资料必须属于某个学科。
3. 资料列表可以按学科筛选。
4. RAG 问答只检索指定学科下的资料切片。
5. AI 出题默认基于整个学科，可选进一步限定到该学科内的某份资料。
6. 当前 Vue 测试台支持创建学科、选择当前学科、按学科上传资料、问答和出题。

## 非目标

- 不做学科共享、班级空间或管理员权限。
- 不做学科层级，例如“后端 / Java / Spring”。
- 不做删除学科时级联删除资料，避免误删上传文件。
- 不迁移旧资料到默认学科；旧资料没有学科时不参与新的学科问答和出题闭环。

## 数据模型

新增 `subjects` 表：

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `user_id` | 所属用户 |
| `name` | 学科名称，例如 `Java` |
| `created_at` | 创建时间 |

约束与索引：

- `INDEX idx_subjects_user_id (user_id)`
- `UNIQUE KEY uk_subject_user_name (user_id, name)`

调整 `materials` 表：

| 字段 | 说明 |
|---|---|
| `subject_id` | 所属学科 ID |

兼容说明：

- 数据库字段允许为空，以兼容已有旧资料。
- 新上传资料必须传 `subjectId`，业务层不允许再创建无学科资料。
- 没有学科的旧资料不出现在按学科筛选的资料列表中，也不参与按学科问答和出题。

索引与约束：

- `INDEX idx_materials_subject_id (subject_id)`
- 外键 `subject_id -> subjects(id)`

`Material` 实体增加 `subjectId` 字段，`MaterialResponse` 增加 `subjectId` 字段，便于前端过滤和展示。

## 后端接口

### 学科接口

`POST /api/subjects`

请求：

```json
{ "name": "Java" }
```

行为：

- 需要登录。
- 学科名去除首尾空白后保存。
- 空名称报错。
- 同一用户下重名报错。

响应数据：

```json
{
  "id": 1,
  "name": "Java",
  "createdAt": "2026-06-19T10:00:00"
}
```

`GET /api/subjects`

行为：

- 返回当前用户自己的学科，按创建时间倒序。

`DELETE /api/subjects/{id}`

行为：

- 只能删除当前用户自己的学科。
- 如果学科下还有资料，报错：`请先删除该学科下的资料`。
- 删除空学科成功返回空数据。

### 资料接口

`POST /api/materials?subjectId=1`

行为：

- `subjectId` 必填。
- 后端校验该学科属于当前用户。
- 资料保存时写入 `subject_id`。
- 文件处理、预览生成、切片入库沿用现有流程。

`GET /api/materials?subjectId=1`

行为：

- `subjectId` 必填。
- 后端校验该学科属于当前用户。
- 只返回该学科下的资料。

`DELETE /api/materials/{id}`

行为：

- 继续按当前用户鉴权。
- 删除资料时清理切片、物理文件和预览文件。

### 问答接口

`POST /api/chat`

请求：

```json
{
  "subjectId": 1,
  "question": "Java 的封装是什么？"
}
```

行为：

- `subjectId` 必填。
- 后端校验学科属于当前用户。
- `RagService` 调用按学科过滤的检索方法。
- 只使用该学科下资料切片作为上下文。
- 若该学科下没有相关资料，沿用当前兜底回答逻辑，并返回空资料来源。

### 出题接口

`POST /api/quiz`

默认按整个学科出题：

```json
{
  "subjectId": 1,
  "type": "single",
  "count": 5
}
```

可选按某份资料出题：

```json
{
  "subjectId": 1,
  "materialId": 10,
  "type": "single",
  "count": 5
}
```

行为：

- `subjectId` 必填。
- `materialId` 可选。
- 只传 `subjectId` 时，汇总该学科下的资料切片作为上下文。
- 同时传 `materialId` 时，校验该资料属于当前用户且属于该学科，再基于这份资料出题。
- 如果没有可用文本，返回：`资料未处理完成或没有可用文本，请稍后再试`。

## 服务层设计

新增：

- `Subject`
- `SubjectRepository`
- `SubjectService`
- `SubjectServiceImpl`
- `SubjectController`
- `CreateSubjectRequest`
- `SubjectResponse`

调整：

- `MaterialRepository`
  - 增加按用户和学科查询资料。
  - 增加判断学科下是否存在资料。
- `MaterialService`
  - `upload(MultipartFile file, Long subjectId)`
  - `list(Long subjectId)`
  - 上传前校验学科归属。
- `DocumentIngestionService`
  - 增加 `retrieve(Long userId, Long subjectId, String query, int topK)`。
  - 增加 `getSubjectContext(Long userId, Long subjectId, int maxChunks)`。
  - 保留现有按单资料取上下文的方法，供可选 `materialId` 出题使用。
- `RagServiceImpl`
  - 从 `ChatRequest` 读取 `subjectId`。
  - 使用按学科过滤的检索方法。
- `QuizServiceImpl`
  - 从 `QuizRequest` 读取 `subjectId` 和可选 `materialId`。
  - 优先按单资料上下文出题，否则按整学科上下文出题。

## 切片检索策略

当前项目使用数据库中的 `material_chunks` 做关键词检索。为了按学科过滤，检索时可以先找到该用户该学科下的资料 ID，再只遍历这些资料对应的切片。

实现方式：

- `MaterialChunkRepository` 增加 `findByUserIdAndMaterialIdIn(Long userId, Collection<Long> materialIds)`。
- `DocumentIngestionServiceImpl` 复用现有关键词评分逻辑，只替换候选切片集合。

这样不需要把 `subject_id` 冗余到 `material_chunks`，避免资料移动学科时还要批量更新切片。

## 前端测试台设计

在现有 `frontend/src/App.vue` 中增加学科管理能力：

- 新增 `Subject` 类型与 `subjects` 状态。
- 新增 `subjectName` 和 `selectedSubjectId`。
- 新增按钮：创建学科、刷新学科。
- 新增选择框：当前学科。
- 上传资料时将当前学科作为 `subjectId` 查询参数。
- 刷新资料列表时传当前学科。
- 问答请求体增加 `subjectId`。
- 出题请求体增加 `subjectId`，`materialId` 改为可选。
- 出题资料选择框增加“整个学科”选项。

页面流程：

1. 登录。
2. 创建或选择“Java”学科。
3. 上传资料。
4. 刷新当前学科资料列表。
5. 在问答区提问，AI 只基于 Java 学科资料回答。
6. 在出题区选择“整个学科”或某份资料生成题目。

## 错误处理

- 未登录：沿用现有 `未登录`。
- 学科不存在或不属于当前用户：`学科不存在或无权访问`。
- 学科名为空：`学科名称不能为空`。
- 学科重名：`学科已存在`。
- 删除非空学科：`请先删除该学科下的资料`。
- 上传未传学科：`subjectId 不能为空`。
- 出题无可用上下文：`资料未处理完成或没有可用文本，请稍后再试`。

## 测试策略

后端采用 TDD 增量实现：

1. `SubjectServiceImplTest`
   - 创建学科成功。
   - 空名称失败。
   - 重名失败。
   - 只返回当前用户学科。
   - 删除非空学科失败。
2. `MaterialServiceImplTest`
   - 上传时校验学科归属。
   - 列表只返回指定学科资料。
   - `MaterialResponse` 包含 `subjectId`。
3. `DocumentIngestionServiceImplTest`
   - 按 `subjectId` 检索只返回该学科资料切片。
   - 整学科上下文汇总多个资料的切片。
4. `RagServiceImplTest`
   - 问答请求使用指定学科检索。
5. `QuizServiceImplTest`
   - 只传 `subjectId` 时使用整学科上下文。
   - 同时传 `materialId` 时校验资料属于该学科。

前端验证：

- `npm` 构建通过。
- 手动或浏览器验证测试台可以完成“登录 -> 创建 Java 学科 -> 上传资料 -> 问答 -> 出题”闭环。

## 实施顺序

1. 增加学科实体、仓库、DTO、服务和控制器。
2. 更新 schema。
3. 给资料模型和接口接入 `subjectId`。
4. 给文档检索和上下文汇总接入学科过滤。
5. 调整问答和出题 DTO 与服务。
6. 更新 Vue 测试台。
7. 跑后端测试和前端构建。
