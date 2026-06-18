# 文件预览功能设计:Word/PPT/Excel → PDF

- 日期:2026-06-18
- 状态:已确认,待实现
- 范围:后端(RevMate backend, Spring Boot 3.4 / Java 21)

## 1. 目标

为已上传的资料提供在线预览能力。Word/PPT/Excel 转换为 PDF 后供前端渲染;PDF 原文件直接预览。预览内容必须经过用户鉴权,不暴露后端文件路径。

## 2. 现状与差距

现有上传链路只支持 **txt / pdf / word / image**:

- `MaterialServiceImpl.ALLOWED_MIME_TYPES` 不含 ppt / excel
- `mimeTypeToContentType` 不会返回 `ppt` / `excel`
- `ExtractorRouter.init` 只为 `txt/pdf/word/image` 建立映射
- `TikaExtractor.supports` 只认 `txt/pdf/word`

因此本功能分两部分:

- **A. 打通 PPT/Excel 上传与文本提取**(前置)
- **B. PDF 预览**(主体)

`Material` 实体已有 `storagePath`(注释:仅后端内部使用,不对外暴露),预览必须走鉴权接口流式返回。

## 3. 决策(已确认)

- 转换时机:**上传时预转**,挂在现有 `@Async` 处理链路中
- 支持类型:Word(doc/docx)、PPT(ppt/pptx)、Excel(xls/xlsx)、PDF(原文件)
- 部署:本地 Windows 开发优先跑通,线上部署方式后续再定
- 范围:A + B 全做

## 4. 技术选型

JODConverter (local) + LibreOffice 无头模式(`soffice`)。

- 免费、对 Office 格式高保真
- 需在运行环境安装 LibreOffice
- 文本提取沿用 Tika(`tika-parsers-standard-package` 已能解析 ppt/excel),无需新增提取器

## 5. 改动清单

### A. 放开 PPT/Excel 上传与提取

- `MaterialServiceImpl.ALLOWED_MIME_TYPES`:新增
  - `application/vnd.openxmlformats-officedocument.presentationml.presentation`(pptx)
  - `application/vnd.ms-powerpoint`(ppt)
  - `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`(xlsx)
  - `application/vnd.ms-excel`(xls)
- `MaterialServiceImpl.mimeTypeToContentType`:ppt 系映射为 `"ppt"`,excel 系映射为 `"excel"`
- `MaterialServiceImpl.detectMimeType` 降级 switch:补 `ppt/pptx/xls/xlsx` 扩展名
- `ExtractorRouter.init`:类型列表加入 `"ppt"`、`"excel"`
- `TikaExtractor.supports`:加 `"ppt"`、`"excel"`

### B. PDF 预览

- `Material` 实体:新增 `@Column(name="preview_path", length=512) String previewPath`
  - pdf 类型:previewPath = storagePath
  - word/ppt/excel:转换后的 PDF 路径
  - 其他(txt/image)或转换失败:null
- 新增 `PdfConversionService`:
  - 封装 JODConverter 的 `DocumentConverter`
  - 方法 `Path convertToPdf(Path source, String type)`,输出到 `uploads/{userId}/preview/{原名}.pdf`
  - 仅对 word/ppt/excel 执行;失败抛出受检异常由调用方降级处理
- `FileProcessingService.processFile`:文本提取+入库之后,
  - 若 type 为 pdf:previewPath = storagePath
  - 若 type 为 word/ppt/excel:调用 `PdfConversionService` 转换,成功则写回 previewPath
  - 转换失败:记日志,previewPath 保持 null,**不影响** status 置为 READY
- `MaterialController` 新增 `GET /api/materials/{id}/preview`:
  - 校验当前用户 == material.userId,否则拒绝
  - previewPath 为 null → 404 / 「预览不可用」
  - 否则流式返回 PDF:`Content-Type: application/pdf`,`Content-Disposition: inline`
- `MaterialResponse`:新增布尔 `previewable`(previewPath != null),前端据此显示预览按钮
- `MaterialServiceImpl.delete` / `deletePhysicalFile`:删除资料时一并删除 previewPath 指向的 PDF(best-effort)
- `pom.xml`:新增依赖
  - `org.jodconverter:jodconverter-local`
  - `org.jodconverter:jodconverter-spring-boot-starter`
- 配置(`application.yml`):
  - `jodconverter.local.enabled: true`
  - `jodconverter.local.office-home`(LibreOffice 安装路径,可留空自动探测)
  - `jodconverter.local.max-tasks-per-process` / 进程池大小(默认即可)

## 6. 数据流

```
上传文件
  → MaterialServiceImpl.upload:存盘、检测 MIME、校验白名单、记录 Material(PROCESSING)
  → FileProcessingService.processFile (@Async):
       1. ExtractorRouter 提取文本
       2. DocumentIngestionService 切片 + 向量入库
       3. 按 type 生成 previewPath:
            pdf            → = storagePath
            word/ppt/excel → PdfConversionService 转换
            其他/失败       → null
       4. Material.status = READY,保存 previewPath

前端
  → GET /api/materials 看到 previewable
  → 点预览 → GET /api/materials/{id}/preview(鉴权)→ PDF 流
  → iframe / pdf.js 渲染
```

## 7. 错误处理

- 转换失败不阻断主流程:文本已入库,status 仍可 READY,previewPath=null,previewable=false
- 预览接口遇 previewPath=null:返回 404 或业务错误「预览不可用」
- LibreOffice 未安装 / JODConverter 启动失败:记录日志,转换功能降级为不可用,**不影响应用启动**与其他功能
- 删除资料:previewPath PDF 删除失败采用 best-effort,不阻断删库(与现有 `deletePhysicalFile` 一致)

## 8. 测试

- `PdfConversionService` 单测:给样例 docx,转出非空 PDF 文件
- `MaterialController` 预览接口测试:
  - 本人资料且 previewPath 存在 → 200 + application/pdf
  - 他人资料 → 拒绝(403/业务错误)
  - previewPath 为 null → 404 / 预览不可用
- 回归:沿用现有 `MaterialServiceImplTest`,确认放开 ppt/excel 后上传与类型映射正确

## 9. 非目标(YAGNI)

- 不做 txt / image 的 PDF 化预览(前端可直接展示)
- 不做转换结果的多分辨率/缩略图
- 不在本设计内确定线上部署形态(Docker vs 裸机),仅保证本地可跑通
