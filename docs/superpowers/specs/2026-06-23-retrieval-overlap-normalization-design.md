# 检索改进设计：分块重叠 + 归一化融合

- 日期：2026-06-23
- 范围：仅第 1+2 步（分块加重叠、融合分归一化），**不含** LLM 重排
- 借鉴来源：`weiwill88/Local_Pdf_Chat_RAG`（文本切分带重叠、`hybrid_merge` 归一化后加权）
- 影响文件：`backend/src/main/java/com/team/study/service/DocumentIngestionServiceImpl.java`

## 背景

RevMate 当前检索（详见 `DocumentIngestionServiceImpl` 与 `RagServiceImpl`）存在两处可低成本改进：

1. **分块零重叠**：`splitText` 按 `\n` 累加到 500 字符即切，相邻片段无重叠，关键句被切在边界时两侧都召不回。
2. **融合分量纲不一**：`score = vectorScore*0.75 + keywordScore*0.25` 直接用原始分。`vectorScore` 是余弦（≤1），`keywordScore` 是启发式（精确匹配可达 1.8+），导致关键词分易压过向量分，0.75/0.25 的权重比例名不副实。

本设计只做这两项纯算法改动：零新依赖、零额外延迟、零外部调用。

## 关键约束（最重要）

现有命中判定 `RagServiceImpl.hasReliableMaterialScore` 与逐切片精筛 `filterRelevantChunks` 都依赖**原始余弦** `vectorScore ≥ MIN_VECTOR_SCORE(0.40)`，关键词降级路径依赖 `keywordScore ≥ MIN_KEYWORD_SCORE(0.12)`。

**原则：归一化只作用于「排序用的融合分 `score`」，绝不改动 `vectorScore` / `keywordScore` 这两个写入 metadata 的原始值。** 因此 `RagServiceImpl` 的判定与精筛逻辑一行都不改。

## 范围决策（已与用户确认）

- 覆盖范围：第 1+2 步，不做重排。
- 旧数据：**不迁移**，新分块逻辑只对新上传的资料生效；已入库切片保持原样。
- 不引入新依赖、配置开关或外部模型。

## 第 1 步：分块加重叠（`splitText`）

### 现状
`DocumentIngestionServiceImpl.splitText(text, 500)`：按 `\n` 切段，累加到 `maxLen` 即落一片；超长单段按 `maxLen` 硬切。相邻片段无重叠。

### 改法
- 新增常量：`CHUNK_SIZE = 500`（维持现状）、`CHUNK_OVERLAP = 50`（沿用 #1 的 ~10% 比例）。
- 每落一片后，将该片**末尾约 `CHUNK_OVERLAP` 个字符**作为下一片的起始内容，再继续累加。
- 超长单段硬切时，相邻子片之间同样保留 `CHUNK_OVERLAP` 重叠（步进为 `maxLen - overlap`）。

### 边界与防御
- `CHUNK_OVERLAP` 必须 `< CHUNK_SIZE`。
- 重叠回带后若导致切分不前进（步进 ≤ 0），跳过重叠以避免死循环。
- 空白片继续跳过；首片无前序，不带重叠。

## 第 2 步：归一化后加权（`retrieveFromChunks`）

### 现状
```
double score = queryEmbedding == null
        ? keywordScore
        : vectorScore * 0.75 + keywordScore * 0.25;
```

### 改法（借 #1 `hybrid_merge` 归一化思想）
1. 先扫一遍候选，求出本批 `maxKeywordScore`。
2. 融合分：
   ```
   normKeyword = maxKeyword > 0 ? keywordScore / maxKeyword : 0.0
   score = vectorScore * 0.75 + normKeyword * 0.25     // 有 embedding
         = keywordScore                                 // 无 embedding，降级路径不变
   ```
3. `vectorScore` 本身已是余弦（0~1），不二次归一，直接进融合。
4. metadata 中 `vectorScore`、`keywordScore` 仍写**原始值**；仅 `score`（排序键）使用归一化结果。

实现上需在循环前先算出 `maxKeywordScore`（可先遍历候选计算 keywordScore 并暂存，再统一组装 Document），排序、TopK、下游逻辑均不动。

## 数据流（改动后）

**入库**：`ingest` → `splitText`（✦带重叠）→ `fillEmbeddings` → 存 MySQL。仅新上传走新切法。

**检索**：`retrieve` → `retrieveFromChunks`：算 `vectorScore`(余弦) + `keywordScore`(原启发式不变) → ✦求批内 `maxKeyword` → ✦`score = 0.75*vectorScore + 0.25*(keyword/maxKeyword)` → 按 `score` 降序 → TopK。metadata 三个分原样带出 → `RagServiceImpl` 命中判定/精筛**完全不变**。

## 错误处理与兼容

- `maxKeyword == 0`（全无关键词命中）：`normKeyword = 0`，`score` 退化为纯向量分，不除零。
- embedding 不可用：`score = keywordScore`，与现状一致，不进归一化分支。
- 重叠参数防御见第 1 步。
- 向后兼容：旧切片只有原始分，检索时与新切片一视同仁；无新增字段需求。

## 测试方案

- **`splitText` 重叠**（新单测）：给定跨段/超长文本，断言相邻片存在 `CHUNK_OVERLAP` 量级重叠、内容无丢失、超长段硬切也带重叠。
- **融合归一化**（新单测）：
  - 构造「关键词分高余弦低」vs「余弦高关键词低」两切片，断言归一化后余弦主导排序（修正旧行为）。
  - 断言 `maxKeyword = 0` 不抛异常。
  - 断言 metadata 中 `vectorScore`/`keywordScore` 仍为原始值。
- **回归**：跑现有 `DocumentIngestionServiceImplTest` + `RagServiceImplTest`（13 项），确保命中判定/精筛不回归。
- 验证命令：`mvnw -q test`（后端环境 Maven 可能不可用，使用项目自带 `mvnw`）。

## 非目标（YAGNI）

- LLM / CrossEncoder 重排——本期不做。
- 旧数据重切/重建 embedding——本期不做。
- 迁移到 FAISS/向量库、真·BM25——本期不做。
- 任何配置开关——本期不做。
