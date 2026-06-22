# RevMate API 接口文档

**基础地址：** `http://localhost:8080`

**统一返回格式：**
```json
{
  "code": 0,        // 0=成功，非0=失败
  "message": "ok",  // 成功时"ok"，失败时错误描述
  "data": {}        // 响应数据
}
```

**鉴权方式：** JWT Token，请求头加 `Authorization: Bearer {token}`

---

## 1. 基础

### 1.1 心跳检查

```
GET /api/ping
```

**响应：**
```json
{"code":0,"data":{"message":"pong"}}
```

---

## 2. 用户认证

### 2.1 注册

```
POST /api/auth/register
Content-Type: application/json
```

**请求体：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名，3~50字符 |
| password | string | 是 | 密码，6~100字符 |

**示例：**
```json
{"username":"test","password":"123456"}
```

**响应：** `{"code":0,"message":"ok"}`

### 2.2 登录

```
POST /api/auth/login
Content-Type: application/json
```

**请求体：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

**响应：**
```json
{"code":0,"data":{"token":"eyJhbGciOiJIUzM4NCJ9..."}}
```

---

## 3. 资料管理

### 3.1 上传资料

```
POST /api/materials
Content-Type: multipart/form-data
Authorization: Bearer {token}
```

**参数：** `file`（文件，支持 txt/pdf/doc/docx/ppt/pptx/jpg/png/gif/bmp/webp）

**响应：**
```json
{
  "code": 0,
  "data": {
    "id": 1,
    "filename": "test.txt",
    "type": "txt",
    "status": "PROCESSING",
    "createdAt": "2026-06-22T10:00:00"
  }
}
```

| 字段 | 说明 |
|------|------|
| id | 资料ID，后续操作使用 |
| status | PROCESSING（处理中）→ READY（就绪）/ FAILED（失败） |

### 3.2 资料列表

```
GET /api/materials
Authorization: Bearer {token}
```

**响应：**
```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "filename": "test.txt",
      "type": "txt",
      "status": "READY",
      "createdAt": "2026-06-22T10:00:00"
    }
  ]
}
```

### 3.3 删除资料

```
DELETE /api/materials/{id}
Authorization: Bearer {token}
```

**响应：** `{"code":0,"message":"ok"}`

---

## 4. RAG 问答

### 4.1 普通问答

```
POST /api/chat
Content-Type: application/json
Authorization: Bearer {token}
```

**请求体：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| question | string | 是 | 用户问题 |

**响应：**
```json
{
  "code": 0,
  "data": {
    "answer": "RAG是检索增强生成技术。",
    "sources": [
      {
        "type": "material",
        "title": "test.txt",
        "snippet": "RAG（检索增强生成）是一种...",
        "materialId": 1
      }
    ]
  }
}
```

| sources 字段 | 说明 |
|---|---|
| type | `material`（资料来源）/ `web`（联网搜索） |
| title | 文件名 |
| snippet | 原文片段（前150字） |
| materialId | 资料ID |

### 4.2 流式问答（SSE 逐字显示）

```
POST /api/chat/stream
Content-Type: application/json
Authorization: Bearer {token}
```

**请求体：** 同普通问答

**响应格式：** `text/event-stream`

```
data:RAG是
data:检索增强
data:生成技术
data:[DONE]
```

**前端示例（Vue3）：**
```javascript
const resp = await fetch('/api/chat/stream', {
  method: 'POST',
  headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
  body: JSON.stringify({ question: '什么是 RAG？' })
});
const reader = resp.body.getReader();
const decoder = new TextDecoder();
while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  text += decoder.decode(value);
}
```

---

## 5. AI 出题

### 5.1 生成题目

```
POST /api/quiz
Content-Type: application/json
Authorization: Bearer {token}
```

**请求体：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| materialId | number | 是 | 资料ID |
| type | string | 是 | 题型：`single`（单选）/ `fill`（填空）/ `qa`（简答） |
| count | number | 否 | 题目数量，1~20，默认5 |

**响应：**
```json
{
  "code": 0,
  "data": {
    "questions": [
      {
        "stem": "RAG的全称是什么？",
        "options": ["A. 检索增强生成", "B. 随机算法生成", "C. 递归自动生成", "D. 实时分析生成"],
        "answer": "A",
        "analysis": "RAG是Retrieval-Augmented Generation的缩写"
      }
    ]
  }
}
```

| QuestionItem 字段 | 说明 |
|---|---|
| stem | 题干 |
| options | 选项列表（仅单选有，填空/简答为空） |
| answer | 答案 |
| analysis | 解析 |

---

## 错误码

| code | 说明 |
|------|------|
| 0 | 成功 |
| 400 | 参数错误（如用户名已存在、文件类型不支持） |
| 401 | 未登录或 token 过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 支持的文件类型

| 格式 | 提取方式 |
|---|---|
| .txt | Tika 直接读取文本 |
| .pdf（文字版） | Tika 提取文字 |
| .pdf（扫描版） | PDFBox 渲染图片 → Qwen-VL 视觉模型 OCR |
| .doc / .docx | Tika 提取文字 |
| .ppt / .pptx | Tika 提取文字 |
| .jpg / .png / .gif / .bmp / .webp | Qwen-VL 视觉模型识别 |
