# RevMate

RevMate 是一个面向复习场景的智能学习助手。用户可以按学科管理资料，上传文本、PDF、Office 文档、图片或音频，系统会完成文本提取、资料切片和本地入库，并在此基础上提供 RAG 问答、资料预览、AI 出题、错题本和课程推荐等能力。

本项目是前后端分离的课程设计项目：

- 后端：Spring Boot + Spring AI，负责鉴权、资料处理、RAG、出题、错题本、课程推荐和文件预览。
- 前端：Vue 3 + Vite + Element Plus，负责登录、资料管理、智能问答、AI 出题、错题本和推荐课程页面。

## 功能概览

### 用户与学科

- 支持注册、登录和 JWT 鉴权。
- 每个用户拥有独立的数据空间。
- 资料、问答历史、错题和收藏课程按用户与学科隔离。

### 资料管理

- 支持上传 `txt`、`pdf`、`doc/docx`、`ppt/pptx`、`xls/xlsx`、图片和音频文件。
- 使用 Apache Tika 提取文本内容。
- 支持图片 OCR、扫描版 PDF OCR 和音频转写。
- 支持资料处理状态展示：`PROCESSING`、`READY`、`FAILED`。
- 支持资料删除和重新索引。

### 资料预览

- PDF 可直接预览。
- Word、PPT、Excel 通过 LibreOffice/JODConverter 转为 PDF 后预览。
- 资料文本处理状态和预览状态分离：资料可用于问答，不代表 PDF 预览一定成功。

### 智能问答

- 支持普通问答和 SSE 流式问答。
- 优先基于用户资料检索回答，并返回原文出处。
- 可在指定学科或指定资料范围内问答。
- 支持聊天历史保存、更新、删除和清空。
- 可接入联网搜索，作为资料不足时的补充来源。

### AI 出题与错题本

- 支持单选、填空、简答三类题型。
- 可根据学科或资料生成题目。
- 错题本支持批量保存、手动加入、标记掌握、删除和强化练习。
- 同一用户同一学科下会对重复题干做合并统计。

### 推荐课程

- 可从资料中提取学习关键词。
- 根据关键词生成课程推荐。
- 支持收藏和删除推荐课程。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.4.4、Spring Web、Spring Security、Spring Data JPA |
| AI | Spring AI 1.0.0 OpenAI 兼容接口 |
| 文档解析 | Apache Tika 3.1.0 |
| Office 预览 | LibreOffice、JODConverter |
| 数据库 | MySQL 8 |
| 鉴权 | JWT |
| 前端 | Vue 3、Vite、TypeScript、Vue Router、Pinia、Element Plus |
| HTTP | Axios、SSE fetch stream |

## 项目结构

```text
RevMate/
├── backend/                         # Spring Boot 后端
│   ├── src/main/java/com/team/study
│   │   ├── controller/              # REST API
│   │   ├── service/                 # 业务逻辑
│   │   ├── extractor/               # 文档、图片、音频提取
│   │   ├── repository/              # JPA Repository
│   │   ├── entity/                  # 数据库实体
│   │   ├── dto/                     # 请求与响应 DTO
│   │   └── security/                # JWT 与安全配置
│   ├── src/main/resources
│   │   ├── application.yml          # 默认配置
│   │   ├── application-mysql.yml    # MySQL profile 配置
│   │   └── schema.sql               # 数据库初始化脚本
│   └── pom.xml
├── frontend/                        # Vue 3 前端
│   ├── src/api/                     # 前端 API 封装与类型
│   ├── src/views/                   # 页面
│   ├── src/components/              # 组件
│   ├── src/router/                  # 路由
│   └── package.json
├── docs/                            # API、设计文档、分工计划
├── uploads/                         # 本地上传与预览文件目录
└── README.md
```

## 环境要求

- JDK 21
- MySQL 8
- Node.js 20+
- npm
- LibreOffice
- Maven Wrapper：项目已提供 `backend/mvnw.cmd`

可选能力需要额外服务：

- 图片 OCR / 扫描 PDF OCR：需要可用的视觉模型。
- 音频转写：需要 DashScope 录音文件识别和阿里云 OSS。
- 联网搜索：需要博查或 Serper API Key。
- 向量检索：需要开启 embedding 配置。

## 后端配置

后端默认读取 `backend/src/main/resources/application.yml`。最少需要配置模型 Key 和 JWT 密钥。

PowerShell 示例：

```powershell
$env:OPENAI_API_KEY="你的 API Key"
$env:OPENAI_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode"
$env:OPENAI_MODEL="qwen3.5-omni-plus"
$env:JWT_SECRET="至少 32 个字符的随机密钥"
$env:MYSQL_PASSWORD="你的 MySQL root 密码"
```

如果要开启 embedding：

```powershell
$env:EMBEDDING_ENABLED="true"
$env:EMBEDDING_MODEL="text-embedding-v4"
```

如果要使用联网搜索：

```powershell
# 可选值：bocha / serper
$env:SEARCH_PROVIDER="bocha"
$env:BOCHA_API_KEY="你的博查 API Key"

# 或使用 Serper
$env:SEARCH_PROVIDER="serper"
$env:SERPER_API_KEY="你的 Serper API Key"
```

如果要上传音频资料并转写：

```powershell
$env:DASHSCOPE_API_KEY="你的 DashScope API Key"
$env:DASHSCOPE_ASR_MODEL="paraformer-v2"
$env:ALIYUN_OSS_ENDPOINT="oss-cn-xxx.aliyuncs.com"
$env:ALIYUN_OSS_BUCKET="你的 OSS bucket"
$env:ALIYUN_ACCESS_KEY_ID="你的 AccessKeyId"
$env:ALIYUN_ACCESS_KEY_SECRET="你的 AccessKeySecret"
```

说明：

- `DASHSCOPE_API_KEY` 默认会回退到 `OPENAI_API_KEY`。
- 音频转写需要先把本地音频上传到 OSS，再生成临时签名 URL 交给 DashScope。
- 未配置 OSS 时，文本、PDF、Office 和图片资料不受影响；音频处理会失败并返回缺失配置说明。

## 数据库初始化

先确保本地 MySQL 可连接，然后执行：

```powershell
mysql -u root -p < backend/src/main/resources/schema.sql
```

开发环境下也可以直接启动后端。当前 JPA 配置为 `ddl-auto: update`，会根据实体自动补齐表结构。

## 启动项目

### 1. 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

后端默认端口为 `8080`，健康检查接口：

```text
GET http://localhost:8080/api/ping
```

### 2. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

Vite 已配置 `/api` 代理到 `http://localhost:8080`，因此开发时需要先启动后端。

## 常用接口

完整接口说明见 `docs/API.md`。主要接口包括：

| 模块 | 接口 |
| --- | --- |
| 心跳 | `GET /api/ping` |
| 注册 | `POST /api/auth/register` |
| 登录 | `POST /api/auth/login` |
| 学科 | `GET/POST/PUT/DELETE /api/subjects` |
| 资料 | `GET/POST/DELETE /api/materials` |
| 资料预览 | `GET /api/materials/{id}/preview` |
| 音频与转写 | `GET /api/materials/{id}/audio`、`GET /api/materials/{id}/transcript` |
| 问答 | `POST /api/chat`、`POST /api/chat/stream` |
| 聊天历史 | `GET/PUT/DELETE /api/chat/history` |
| 出题 | `POST /api/quiz` |
| 错题本 | `GET/POST/PATCH/DELETE /api/wrong-questions` |
| 课程推荐 | `POST /api/courses/keywords`、`POST /api/courses/recommend`、`GET/POST/DELETE /api/courses/saved` |

需要登录的接口统一使用：

```text
Authorization: Bearer <token>
```

## 前端页面

| 路径 | 页面 |
| --- | --- |
| `/login` | 登录 / 注册 |
| `/home` | 智能问答 |
| `/materials` | 我的资料 |
| `/materials/:id` | 资料预览 |
| `/quiz` | AI 出题 |
| `/wrong-questions` | 错题本 |
| `/recommend` | 推荐课程 |

## 运行测试

后端测试：

```powershell
cd backend
.\mvnw.cmd test
```

前端构建检查：

```powershell
cd frontend
npm run build
```

## 常见问题

### 资料显示 READY，但不能预览

`status=READY` 只表示文本已经提取完成，可以用于问答和出题。预览需要看 `previewStatus`：

- `previewStatus=READY`：可以预览。
- `previewStatus=PROCESSING`：正在转换。
- `previewStatus=FAILED`：转换失败，通常需要检查 LibreOffice 是否安装、文件是否损坏。

### Office 文档预览失败

请确认本机安装了 LibreOffice，并且命令行可访问 LibreOffice。后端通过 JODConverter 调用 LibreOffice 将 Word、PPT、Excel 转为 PDF。

### 音频资料处理失败

请检查 DashScope 与 OSS 相关环境变量是否完整。DashScope 录音文件识别需要服务端可访问的音频 URL，因此本地音频必须先上传到 OSS。

### 问答没有引用资料

可能原因：

- 资料仍在 `PROCESSING` 或处理失败。
- 问题与当前学科或资料内容相关性不足。
- 当前未开启 embedding，系统会使用关键词和重叠度等方式召回，效果可能弱于向量检索。

### 前端请求 401

登录态失效或缺少 token。重新登录后，前端会在请求头中带上 `Authorization: Bearer <token>`。

## 协作说明

- `main` 分支保持可运行。
- 每个模块在独立分支开发，通过 Pull Request 合并。
- 不提交 API Key、数据库密码、JWT 密钥等敏感信息。
- 本地上传文件和预览文件存放在 `uploads/`，不应提交到仓库。
- 修改接口时需要同步更新后端 DTO、前端 `src/api/types.ts` 和 `docs/API.md`。

## 文档索引

- `docs/API.md`：接口文档。
- `docs/设计文档.md`：项目设计说明。
- `docs/plans/`：成员分工与模块计划。
- `docs/superpowers/specs/`：功能设计记录。
- `docs/superpowers/plans/`：功能实施计划。
