# 推荐课程(Recommended Courses)设计

日期:2026-06-24
分支建议:`feature/recommended-courses`

## 1. 背景与目标

为 RevMate 增加"推荐课程"能力:让大模型联网搜索网上的优质学习资料,推荐给用户。

两个入口:
- **独立页面**:用户输入主题,返回推荐课程列表。
- **按资料**:在资料详情页,针对某份上传资料推荐相关学习资源。

约束:**不引入新的 LLM 调用方式**——必须复用现有 Spring AI `ChatClient`(项目当前为 Spring AI 1.0.0 + OpenAI starter,走 DashScope/Qwen 兼容模式)。

## 2. 关键技术决策

- Qwen 自带的 `enable_search` 需要 DashScope 原生接口或参数透传,而 Spring AI 1.0.0 的 `OpenAiChatOptions` 无 `enable_search` 字段、无任意参数透传口子 —— 因此**排除"让模型自己联网"**。
- 采用 **搜索 API + 现有 ChatClient** 方案:后端先调搜索服务拿真实网页结果,再把结果作为上下文喂给现有 `ChatClient` 整理成结构化推荐。**LLM 调用方式完全不变**,与现有 RAG「检索 → 回答」结构对称,符合约束。
- 搜索服务选 **博查(Bocha)AI 搜索**:国内服务,中文学习资源(B站/慕课/知乎等)覆盖好,有免费额度。需配置一个 API key。
- 纯模型生成(不真正联网)被排除:会编造失效链接,无法保证"优质资料"。

## 3. 整体架构

```
博查搜索 API  ──→  搜索结果(真实网页:标题/链接/摘要)
                        │
                        ▼
              现有 ChatClient(把搜索结果当上下文)
                        │
                        ▼
              结构化课程卡片 JSON  ──→  前端
```

新增两个服务,职责单一:
- `BochaSearchService`:只负责调博查 HTTP 接口、解析返回结果为内部 `WebSearchResult` 列表。可独立测试(mock HTTP)。
- `CourseRecommendService`:编排逻辑——(资料场景)提炼关键词 → 调 `BochaSearchService` 搜索 → 用 `ChatClient` 把搜索结果整理成课程卡片。依赖 `BochaSearchService`、`ChatClient`、`DocumentIngestionService`(取资料切片)。

## 4. 数据模型

- **推荐结果不持久化**:每次现搜,保证新鲜。
- **新增 `saved_courses` 表**存收藏:

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| user_id | bigint NOT NULL | 归属用户 |
| title | varchar | 课程标题 |
| platform | varchar | 来源平台(B站/慕课等) |
| url | varchar | 跳转链接 |
| reason | varchar | 一句话推荐理由 |
| difficulty | varchar(20) | 难度标签(入门/进阶等) |
| subject_id | bigint NULL | 可空,记录从哪个科目/资料来的 |
| created_at | datetime | |

对应实体 `SavedCourse`(遵循现有 `Material` 的 JPA 风格:`@Data @Entity @Table(name="saved_courses")`,`@GeneratedValue(IDENTITY)`)+ `SavedCourseRepository`。

## 5. 后端接口

新建 `CourseController`,基址 `/api/courses`,返回统一 `Result<T>`。

| 方法 | 路径 | 入参 | 返回 |
|---|---|---|---|
| POST | `/api/courses/keywords` | `{ materialId }` | 模型从该资料提炼的 2~3 个候选关键词 `List<String>` |
| POST | `/api/courses/recommend` | `{ keywords: string[] }` | 结构化课程卡片数组(默认 6 条) |
| POST | `/api/courses/saved` | 课程卡片字段(可带 subjectId) | 收藏的记录 |
| GET | `/api/courses/saved` | - | 当前用户收藏列表 |
| DELETE | `/api/courses/saved/{id}` | - | 取消收藏 |

流程:
- 独立页面:直接调 `/recommend`(用户自己输入的词)。
- 资料页:先调 `/keywords` 拿候选词 → 用户选/改 → 调 `/recommend`。

课程卡片 DTO(`CourseCard`)字段:`title, platform, url, reason, difficulty`。

用户隔离:所有收藏接口通过 `SecurityUtil` 取当前 userId,只能操作自己的记录。

## 6. 前端

- **新增独立页面 `RecommendView.vue` + 导航入口**(在 `router/index.ts` 与主导航中加项):
  - 顶部:主题输入框 + 搜索按钮 → 下方课程卡片列表。
  - "已收藏"标签页:展示当前用户收藏的课程。
- **资料详情页 `MaterialDetailView.vue`**:加"推荐课程"按钮 → 展开候选关键词 chips(可点选、可编辑)→ 选定后调 `/recommend` 出卡片。
- **`CourseCard.vue`** 组件:标题、平台、难度标签、推荐理由、跳转链接(新标签打开)、收藏/取消按钮。
- **`api/courses.ts`**:封装上述 5 个接口。

## 7. 错误处理

- 博查未配置 key / 调用失败 → 返回友好错误,前端提示"搜索服务暂不可用",不崩溃。
- 搜索结果为空 → 直接返回空数组 + 提示,**不调用模型**(省成本)。
- 模型整理后 JSON 解析失败 → 兜底返回原始搜索结果(标题 + 链接),保证功能至少可用。
- 关键词提炼:资料无切片时,回落为用资料标题作为唯一候选词。

## 8. 测试

- `BochaSearchService`:mock HTTP,验证请求构造与结果解析(含失败/空结果路径)。
- `CourseRecommendService`:mock 搜索 + mock `ChatClient`,验证编排、JSON 解析与兜底逻辑。
- 收藏接口:增 / 删 / 查 + 用户隔离(只能看到/删除自己的)。

## 9. 配置

`application.yml` 新增:
```yaml
bocha:
  api-key: ${BOCHA_API_KEY:}
  base-url: ${BOCHA_BASE_URL:https://api.bochaai.com}
```
key 缺省为空,缺失时按"搜索服务暂不可用"处理。

## 10. 范围外(YAGNI)

- 推荐结果的服务端缓存/历史记录。
- 收藏的分类/标签管理。
- 国际搜索源(Serper/Google)——后续如需精确控制来源再加。
