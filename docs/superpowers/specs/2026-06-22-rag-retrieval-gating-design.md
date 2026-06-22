# RAG 命中判定重构设计

- 日期：2026-06-22
- 范围：RevMate 后端 RAG 问答的「是否命中资料」判定逻辑
- 方案：A（用干净的纯余弦分 + 校准过的阈值做命中判定）

## 背景与问题

RevMate 是课程/Demo 定位的复习助手，数据规模小（每用户几十~几百 chunk），保留现有内存检索即可，重点是**正确性 + 可解释 + 可量化测试**。

运行环境已设 `EMBEDDING_ENABLED=true`、`EMBEDDING_MODEL=text-embedding-v4`，所以向量检索是生效的。

### 观察到的痛点

向用户提与资料无关的问题（如「你是什么模型」），系统仍把用户的参考资料当作依据回答，并挂上来源卡片。即**误命中（false positive）**。召回不足（该命中却答不上来）目前未观察到。

### 根因

稠密 embedding 的余弦相似度有较高的「噪声地板」：`text-embedding-v4` 把任意短问题与技术资料相比，余弦常落在 0.2~0.5。而现有判定 `RagServiceImpl.hasReliableMaterialScore` 用的是**尺度混乱的融合分** `vectorScore * 0.75 + keywordScore * 0.25`，阈值 `MIN_MATERIAL_SCORE = 0.12` 远低于噪声地板，于是几乎任何问题都越过阈值被判为「命中」。`keywordScore` 未归一化（字符级 2/3-gram，中文里「什么」「模型」等高频组合到处匹配）进一步放大噪声。

## 设计

### 核心决策：把「排序」与「命中判定」分开

`DocumentIngestionServiceImpl.retrieveFromChunks` 已经把三个分数分别写入每个 `Document` 的 metadata：`score`（融合）、`keywordScore`、`vectorScore`。因此无需改动检索打分与排序，只需改判定读哪个分数。

- **排序不变**：TopK 仍按融合分 `score` 排序，关键词继续参与排序。
- **判定改为只看纯余弦**：是否走资料分支，只由 `vectorScore` 决定，关键词降级为兜底。

### 判定逻辑

修改 `RagServiceImpl.hasReliableMaterialScore`：

```java
double bestVector  = max(chunk.metadata["vectorScore"]);
double bestKeyword = max(chunk.metadata["keywordScore"]);

boolean hit = bestVector > 0
        ? bestVector  >= MIN_VECTOR_SCORE     // 有向量信号 → 用校准过的余弦阈值
        : bestKeyword >= MIN_KEYWORD_SCORE;   // 无向量信号 → 回退关键词阈值（兜底）
return hit;
```

常量调整（替换旧的 `MIN_MATERIAL_SCORE`）：

- `MIN_VECTOR_SCORE`：初始填保守值 **0.40**，由校准评测 (b) 微调。
- `MIN_KEYWORD_SCORE`：取旧 `MIN_MATERIAL_SCORE` 的 **0.12**。因为 embedding 关闭时旧逻辑的融合分就退化为纯 `keywordScore` 并以 0.12 判定，沿用此值即等价于保留旧的无向量行为，不引入回归。

`bestVector > 0` 天然区分两种情形：有向量信号（embedding 开启且 chunk 有可用向量）走余弦阈值；无向量信号（embedding 关闭或全部 chunk embedding FAILED/null）自动退回关键词判定，**保证现有纯关键词路径不退化**。判定采用 `>=`（恰好等于阈值视为命中）。

### 范围边界

- **仅改无 `materialId` 的学科级检索路径**（`chat` / `chatStream` 中走 `retrieve(...)` 的分支）。痛点就在这条路径。
- **不改带 `materialId` 的路径**（用户主动选定某份资料，`getMaterialContext`）。该路径不应因相关性低而拒答。
- 本次不动检索打分、切片、向量存储、融合权重等其它环节。

## 测试

分两层，测的是不同对象：

### (a) 离线单元测试 —— 守住判定逻辑（进 CI，必跑，毫秒级，无网络）

构造带 metadata 的 chunk，断言判定分支正确。不调用任何外部 API。

| 用例 | 构造 | 期望 |
|---|---|---|
| 有向量信号且高于阈值 | `vectorScore=0.6` | 命中，走资料 |
| 有向量信号但低于阈值 | `vectorScore=0.25` | 不命中，回落通用 |
| 无向量信号但关键词强 | `vectorScore=0, keywordScore=1.0` | 命中（关键词兜底） |
| 无向量信号且关键词弱 | `vectorScore=0, keywordScore=0.1` | 不命中 |
| 边界 | `vectorScore` 恰好等于阈值 | 命中（`>=`） |

这层保证：无论阈值取值如何，判定分支永远走对，且 embedding 关闭时不退化。是防回归主力。

### (b) 校准评测 —— 决定阈值数值（集成测试，opt-in，仅手动跑）

用带标注的小查询集跑真实检索，打印每条 `bestVector`，看相关/无关两类能否被一条线分开。

```
标注集（示例，后续用真实资料替换）：
  相关（应命中）：  "操作系统的死锁条件是什么"
                    "讲一下进程调度算法"
  无关（应回落）：  "你是什么模型"
                    "今天天气怎么样"
                    "帮我写一首诗"
```

输出形如：

```
[HIT ] 操作系统的死锁条件是什么   bestVector=0.62
[HIT ] 进程调度算法              bestVector=0.55
[MISS] 你是什么模型              bestVector=0.24
[MISS] 今天天气怎么样            bestVector=0.19
建议阈值 = (相关最低 + 无关最高)/2 ≈ 0.40
```

用 `@EnabledIfEnvironmentVariable(named="EMBEDDING_ENABLED", matches="true")`（或 `@Tag("calibration")`）标注：普通 `mvn test` 不跑，避免无 key 时失败、避免烧钱。跑一次拿到建议值后填入 `MIN_VECTOR_SCORE`。

### 为什么不把真实余弦放进普通单元测试

1. 依赖外网与 key → 队友/CI 无 key 时 build 红，网络抖动时 flaky。
2. 每次跑都花钱、变慢。
3. 真实余弦随模型版本漂移，无法写死断言。
4. 单元测试应测自己的判定逻辑（喂已知分数即可），而非测外部模型质量。

## 验收

1. 端到端（手动，借助已配置的 `text-embedding-v4`）：问「你是什么模型」→ 走通用回答、不挂资料来源；问资料内问题 → 仍正确命中并挂来源。
2. (a) 离线单元测试全绿，且不依赖网络。
3. (b) 校准评测能跑通并给出建议阈值，相关/无关两类被阈值清晰分开。

## 交付物

1. 改 `RagServiceImpl`：`hasReliableMaterialScore` 逻辑 + 新增 `MIN_VECTOR_SCORE` / `MIN_KEYWORD_SCORE` 常量，移除 `MIN_MATERIAL_SCORE`。
2. 新/改 `RagServiceImplTest`：(a) 的 5 个判定用例。
3. 新增校准测试类（opt-in）：(b) 的标注集 + 打印 + 建议阈值计算。

## 安全提醒（与本设计并行的待办）

设计讨论过程中，一批真实凭据（Aliyun AccessKey Secret、DashScope/OpenAI API Key、JWT_SECRET、MySQL 密码）以截图形式暴露，应视为已泄露。建议尽快在阿里云控制台轮换 AccessKey 与 API Key，并更换 `JWT_SECRET`。此项与代码改动无关，但优先级高。
