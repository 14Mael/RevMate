# RevMate · 复习资料智能学习助手

基于 **Spring Boot + Spring AI + Vue 3** 的复习资料智能学习助手：上传自己的复习资料（文本/PDF/Word/PPT/Excel/图片/音频），系统将资料提取为文本并切片入库，支持基于资料的 **RAG 问答（带原文出处）**、**AI 自动出题**、错题本，以及 Office 文档转 PDF 预览。

> 课程设计作业 · 前后端分离项目。

## 核心功能

1. 多用户登录（每人独立资料库）
2. 资料导入 + RAG 问答（答案带原文出处）
3. PDF / Word / PPT / Excel 资料预览（Office 文档通过 LibreOffice 转 PDF）
4. AI 出题（单选 / 填空 / 简答）
5. 错题本与强化练习

## 技术栈

- 后端：Spring Boot 3.4.x + JDK 21 + Spring AI 1.0.0
- 前端：Vue 3 + Vite + TypeScript + Element Plus + Pinia
- 模型接入：Spring AI OpenAI 兼容接口，可切换 OpenAI / DeepSeek / DashScope 兼容模式
- 数据：MySQL 8，本地文件存储上传资料与预览 PDF

## 目录结构

```
RevMate/
├── README.md          # 项目简介与快速开始
├── backend/           # Spring Boot 后端应用
├── frontend/          # Vue 3 前端应用
├── docs/              # 设计文档、API 文档、分工计划
└── uploads/           # 本地上传文件目录，已加入 .gitignore
```

后端 Maven 构建配置位于 `backend/pom.xml`；前端构建配置位于 `frontend/package.json`。

## 团队分工

| 成员 | 模块 | 分支 |
|---|---|---|
| 1 | 用户与资料管理（后端） | `feat/auth` |
| 2 | RAG 核心与提取器（后端） | `feat/rag` |
| 3 | 出题与 Prompt 工程（后端） | `feat/quiz` |
| 4 | 前端页面与交互 | `feat/frontend` |

## 协作约定

- `main` 始终保持可运行，不直接在 main 上开发
- 每人在自己分支开发，通过 Pull Request 合并到 main
- 开工前先 `git pull origin main` 同步主干，小步提交
- 统一环境：JDK 21 / Maven Wrapper / MySQL 8 / LibreOffice
- 前端统一使用 Node.js 20+ / npm
- API Key、数据库密码使用环境变量或本地配置，**不提交明文密钥**

## 快速开始

### 1. 准备环境

- JDK 21
- MySQL 8
- LibreOffice（用于 Word / PPT / Excel 转 PDF 预览）

### 2. 配置模型环境变量

后端通过 Spring AI 的 OpenAI 兼容接口接入模型。默认 `OPENAI_BASE_URL` 指向 DashScope 兼容模式，必须提供 `OPENAI_API_KEY`。登录鉴权还必须提供 `JWT_SECRET`，长度建议不少于 32 个字符。

PowerShell 示例：

```powershell
$env:OPENAI_API_KEY="你的 API Key"
$env:OPENAI_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode"
$env:OPENAI_MODEL="qwen3.5-omni-plus"
$env:JWT_SECRET="至少 32 个字符的随机密钥"
```

如果切换到 DeepSeek / OpenAI / 其他兼容平台，只需要改 `OPENAI_BASE_URL`、`OPENAI_API_KEY` 和 `OPENAI_MODEL`。

音频资料转写使用 DashScope Paraformer 录音文件识别。该接口需要一个可由 DashScope 服务端访问的音频 URL，因此本地上传的音频会先上传到阿里云 OSS，再生成临时签名 URL 交给 Paraformer。若要上传音频资料，还需要配置：

```powershell
$env:DASHSCOPE_API_KEY="你的 DashScope API Key"
$env:DASHSCOPE_ASR_MODEL="paraformer-v2"
$env:ALIYUN_OSS_ENDPOINT="oss-cn-xxx.aliyuncs.com"
$env:ALIYUN_OSS_BUCKET="你的 OSS bucket"
$env:ALIYUN_ACCESS_KEY_ID="你的 AccessKeyId"
$env:ALIYUN_ACCESS_KEY_SECRET="你的 AccessKeySecret"
```

`DASHSCOPE_API_KEY` 默认会回退到 `OPENAI_API_KEY`，但该 Key 必须已开通 DashScope / 百炼录音文件识别服务。OSS 配置缺失时，普通文本、PDF、Office、图片资料不受影响；音频资料处理会失败并在资料列表中显示具体缺失项。

资料处理状态和预览状态是分开的：`status=READY` 表示资料文本已经可用于问答/出题，`previewStatus=READY` 才表示 PDF 预览可用。若 Word / PPT / Excel 转 PDF 失败，资料仍可用于问答，但接口会返回 `previewStatus=FAILED` 和失败提示，通常需要检查 LibreOffice 是否安装或文件是否损坏。

### 3. 初始化数据库

```powershell
mysql -u root -p < backend/src/main/resources/schema.sql
```

也可以直接启动后端，开发环境下 JPA 会按实体自动补齐表结构。

### 4. 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### 5. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端开发服务器通过 Vite 代理访问后端 `/api` 接口，请先启动后端服务。

### 6. 运行测试

```powershell
cd backend
.\mvnw.cmd test
```
