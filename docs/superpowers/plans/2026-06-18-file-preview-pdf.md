# 文件预览(Word/PPT/Excel → PDF)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 RevMate 资料提供在线预览:Word/PPT/Excel 上传时异步转为 PDF,通过鉴权接口流式返回供前端渲染;PDF 原文件直接预览。

**Architecture:** 在现有 `@Async` 文件处理链路(`FileProcessingService.processFile`)中,文本提取入库后追加 PDF 转换步骤,转换结果路径写入 `Material.previewPath`。新增 `PdfConversionService` 封装 JODConverter+LibreOffice。新增鉴权预览接口流式返回 PDF。先放开 PPT/Excel 的上传与文本提取链路。

**Tech Stack:** Spring Boot 3.4.4 / Java 21 / Maven / JODConverter (local) + LibreOffice / Apache Tika / JPA + MySQL / JUnit 5。

## Global Constraints

- Java 21,Spring Boot parent 3.4.4(沿用现有 pom 版本,勿改)
- 转换失败不得阻断主流程:文本入库后 `status` 仍置 `READY`,`previewPath` 置 null
- `storagePath` / `previewPath` 不对外暴露,文件内容只能经鉴权接口流式返回
- best-effort 删除物理文件:删除失败记日志,不阻断删库(沿用现有 `deletePhysicalFile` 风格)
- 包名 `com.team.study`,日志用 `org.slf4j.Logger`,Lombok `@RequiredArgsConstructor` 注入
- 预览输出目录:`uploads/{userId}/preview/`(`uploads` 取自 `app.upload.dir`,默认 `uploads`)

---

### Task 1: 添加 JODConverter 依赖

**Files:**
- Modify: `backend/pom.xml`

**Interfaces:**
- Produces: classpath 上可用 `org.jodconverter.core.DocumentConverter`、`jodconverter-spring-boot-starter` 的自动配置

- [ ] **Step 1: 在 pom.xml `<dependencies>` 末尾(`</dependencies>` 前)加入依赖**

在 `backend/pom.xml` 的 Test 依赖块之后、`</dependencies>` 之前插入:

```xml
        <!-- JODConverter + LibreOffice (Office 文档转 PDF) -->
        <dependency>
            <groupId>org.jodconverter</groupId>
            <artifactId>jodconverter-local</artifactId>
            <version>4.4.9</version>
        </dependency>
        <dependency>
            <groupId>org.jodconverter</groupId>
            <artifactId>jodconverter-spring-boot-starter</artifactId>
            <version>4.4.9</version>
        </dependency>
```

- [ ] **Step 2: 验证依赖可解析**

Run: `cd backend && ./mvnw -q dependency:resolve -Dsilent=true` (Windows: `mvnw.cmd`)
Expected: 构建成功,无法解析依赖时报错;jodconverter 4.4.9 应可从中央仓库下载。

- [ ] **Step 3: Commit**

```bash
git add backend/pom.xml
git commit -m "build: 添加 jodconverter 依赖用于 Office 转 PDF"
```

---

### Task 2: 放开 PPT/Excel 的上传白名单与类型映射

**Files:**
- Modify: `backend/src/main/java/com/team/study/service/MaterialServiceImpl.java`
- Test: `backend/src/test/java/com/team/study/service/MaterialServiceImplTest.java`

**Interfaces:**
- Produces: `mimeTypeToContentType(String)` 对 ppt 系返回 `"ppt"`、excel 系返回 `"excel"`;`ALLOWED_MIME_TYPES` 接受 ppt/pptx/xls/xlsx

- [ ] **Step 1: 写失败测试**

在 `MaterialServiceImplTest` 增加针对 `mimeTypeToContentType` 的测试。该方法当前为 private,测试用反射调用(与现有测试风格保持一致;若现有测试已有公开入口则复用)。新增:

```java
@Test
void mimeTypeToContentType_mapsPptAndExcel() throws Exception {
    var m = new MaterialServiceImpl(null, null, null, null);
    var method = MaterialServiceImpl.class.getDeclaredMethod("mimeTypeToContentType", String.class);
    method.setAccessible(true);

    assertEquals("ppt", method.invoke(m,
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
    assertEquals("ppt", method.invoke(m, "application/vnd.ms-powerpoint"));
    assertEquals("excel", method.invoke(m,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    assertEquals("excel", method.invoke(m, "application/vnd.ms-excel"));
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `cd backend && ./mvnw -q -Dtest=MaterialServiceImplTest#mimeTypeToContentType_mapsPptAndExcel test`
Expected: FAIL(返回 `"unknown"` 而非 `"ppt"`/`"excel"`)

- [ ] **Step 3: 修改 `ALLOWED_MIME_TYPES`**

在 `MaterialServiceImpl` 的 `ALLOWED_MIME_TYPES` 列表中,`"image/"` 之前加入:

```java
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
```

- [ ] **Step 4: 修改 `mimeTypeToContentType`**

在 `mimeTypeToContentType` 方法中,`image/` 分支之前加入:

```java
        if (mimeType.startsWith("application/vnd.openxmlformats-officedocument.presentationml") ||
            mimeType.startsWith("application/vnd.ms-powerpoint")) return "ppt";
        if (mimeType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml") ||
            mimeType.startsWith("application/vnd.ms-excel")) return "excel";
```

- [ ] **Step 5: 修改 `detectMimeType` 降级 switch**

在 `detectMimeType` 的扩展名 `switch` 中,`"docx"` 分支之后加入:

```java
                case "ppt" -> "application/vnd.ms-powerpoint";
                case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                case "xls" -> "application/vnd.ms-excel";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
```

- [ ] **Step 6: 运行测试,确认通过**

Run: `cd backend && ./mvnw -q -Dtest=MaterialServiceImplTest#mimeTypeToContentType_mapsPptAndExcel test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/team/study/service/MaterialServiceImpl.java backend/src/test/java/com/team/study/service/MaterialServiceImplTest.java
git commit -m "feat: 放开 ppt/excel 上传白名单与类型映射"
```

---

### Task 3: 让提取器支持 ppt/excel

**Files:**
- Modify: `backend/src/main/java/com/team/study/extractor/TikaExtractor.java`
- Modify: `backend/src/main/java/com/team/study/extractor/ExtractorRouter.java`
- Test: `backend/src/test/java/com/team/study/extractor/TikaExtractorTest.java`(新建)

**Interfaces:**
- Consumes: `ContentExtractor.supports(String)`、`ContentExtractor.extract(Resource)`(现有接口)
- Produces: `TikaExtractor.supports("ppt")` 与 `supports("excel")` 返回 true;`ExtractorRouter` 能路由 ppt/excel

- [ ] **Step 1: 写失败测试**

新建 `backend/src/test/java/com/team/study/extractor/TikaExtractorTest.java`:

```java
package com.team.study.extractor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TikaExtractorTest {

    @Test
    void supports_pptAndExcel() {
        TikaExtractor extractor = new TikaExtractor(null);
        assertTrue(extractor.supports("ppt"));
        assertTrue(extractor.supports("excel"));
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `cd backend && ./mvnw -q -Dtest=TikaExtractorTest test`
Expected: FAIL(`supports("ppt")` 返回 false)

- [ ] **Step 3: 修改 `TikaExtractor.supports`**

将 `supports` 方法改为:

```java
    @Override
    public boolean supports(String contentType) {
        return "txt".equals(contentType)
                || "pdf".equals(contentType)
                || "word".equals(contentType)
                || "ppt".equals(contentType)
                || "excel".equals(contentType);
    }
```

- [ ] **Step 4: 修改 `ExtractorRouter.init` 的类型列表**

将 `init()` 中的 `List.of("txt", "pdf", "word", "image")` 改为:

```java
            for (String type : List.of("txt", "pdf", "word", "image", "ppt", "excel")) {
```

- [ ] **Step 5: 运行测试,确认通过**

Run: `cd backend && ./mvnw -q -Dtest=TikaExtractorTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/team/study/extractor/TikaExtractor.java backend/src/main/java/com/team/study/extractor/ExtractorRouter.java backend/src/test/java/com/team/study/extractor/TikaExtractorTest.java
git commit -m "feat: 提取器支持 ppt/excel 文本提取"
```

---

### Task 4: Material 实体新增 previewPath 字段

**Files:**
- Modify: `backend/src/main/java/com/team/study/entity/Material.java`

**Interfaces:**
- Produces: `Material.getPreviewPath()` / `setPreviewPath(String)`(Lombok `@Data` 生成),DB 列 `preview_path`

- [ ] **Step 1: 在 `storagePath` 字段之后新增字段**

在 `Material` 的 `storagePath` 字段声明之后加入:

```java
    /** 预览用 PDF 的本地路径;pdf 类型为原文件路径,不支持预览或转换失败为 null */
    @Column(name = "preview_path", length = 512)
    private String previewPath;
```

- [ ] **Step 2: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/team/study/entity/Material.java
git commit -m "feat: Material 增加 previewPath 字段"
```

---

### Task 5: PdfConversionService(JODConverter 封装)

**Files:**
- Create: `backend/src/main/java/com/team/study/service/PdfConversionService.java`
- Test: `backend/src/test/java/com/team/study/service/PdfConversionServiceTest.java`
- Modify: `backend/src/main/resources/application.yml`(若不存在则 `application.properties`,改为等价 key)

**Interfaces:**
- Consumes: `org.jodconverter.core.DocumentConverter`(由 jodconverter starter 注入)
- Produces: `Path convertToPdf(Path source, Long userId, String baseFilename)` —— 转换成功返回生成的 PDF `Path`;转换失败抛出 `PdfConversionException`(本任务新建的 RuntimeException 子类)。`boolean isConvertibleType(String type)` 判断 word/ppt/excel。

- [ ] **Step 1: 写失败测试**

新建 `backend/src/test/java/com/team/study/service/PdfConversionServiceTest.java`。该测试不依赖真实 LibreOffice,只验证类型判断逻辑:

```java
package com.team.study.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfConversionServiceTest {

    @Test
    void isConvertibleType_trueForOfficeTypes() {
        PdfConversionService service = new PdfConversionService(null);
        assertTrue(service.isConvertibleType("word"));
        assertTrue(service.isConvertibleType("ppt"));
        assertTrue(service.isConvertibleType("excel"));
    }

    @Test
    void isConvertibleType_falseForOthers() {
        PdfConversionService service = new PdfConversionService(null);
        assertFalse(service.isConvertibleType("pdf"));
        assertFalse(service.isConvertibleType("txt"));
        assertFalse(service.isConvertibleType("image"));
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `cd backend && ./mvnw -q -Dtest=PdfConversionServiceTest test`
Expected: FAIL(`PdfConversionService` 不存在,编译失败)

- [ ] **Step 3: 创建 `PdfConversionService`**

新建 `backend/src/main/java/com/team/study/service/PdfConversionService.java`:

```java
package com.team.study.service;

import lombok.RequiredArgsConstructor;
import org.jodconverter.core.DocumentConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 通过 JODConverter + LibreOffice 将 Office 文档转换为 PDF。
 * 仅对 word/ppt/excel 类型生效。
 */
@Service
@RequiredArgsConstructor
public class PdfConversionService {

    private static final Logger log = LoggerFactory.getLogger(PdfConversionService.class);
    private static final Set<String> CONVERTIBLE = Set.of("word", "ppt", "excel");

    private final DocumentConverter documentConverter;

    public boolean isConvertibleType(String type) {
        return type != null && CONVERTIBLE.contains(type);
    }

    /**
     * 将源文件转换为 PDF,输出到 与源文件同目录的 preview/ 子目录。
     * @return 生成的 PDF 路径
     * @throws PdfConversionException 转换失败
     */
    public Path convertToPdf(Path source, String baseFilename) {
        try {
            Path previewDir = source.getParent().resolve("preview");
            Files.createDirectories(previewDir);
            String pdfName = stripExtension(baseFilename) + ".pdf";
            Path target = previewDir.resolve(pdfName);

            File targetFile = target.toFile();
            documentConverter.convert(source.toFile()).to(targetFile).execute();

            log.info("转换 PDF 成功: {} -> {}", source, target);
            return target;
        } catch (Exception e) {
            throw new PdfConversionException("转换 PDF 失败: " + source, e);
        }
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static class PdfConversionException extends RuntimeException {
        public PdfConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

- [ ] **Step 4: 配置 jodconverter(application.yml)**

在 `application.yml` 顶层加入(若是 properties 文件,用等价点号 key):

```yaml
jodconverter:
  local:
    enabled: true
    # office-home 留空让 JODConverter 自动探测 LibreOffice 安装位置;
    # 本地 Windows 如未探测到,显式指定,例如:
    # office-home: C:\Program Files\LibreOffice
    max-tasks-per-process: 100
```

- [ ] **Step 5: 运行测试,确认通过**

Run: `cd backend && ./mvnw -q -Dtest=PdfConversionServiceTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/team/study/service/PdfConversionService.java backend/src/test/java/com/team/study/service/PdfConversionServiceTest.java backend/src/main/resources/application.yml
git commit -m "feat: 新增 PdfConversionService 封装 JODConverter 转 PDF"
```

---

### Task 6: 处理链路接入 PDF 转换

**Files:**
- Modify: `backend/src/main/java/com/team/study/service/FileProcessingService.java`

**Interfaces:**
- Consumes: `PdfConversionService.isConvertibleType(String)`、`PdfConversionService.convertToPdf(Path, String)`;`Material.setPreviewPath(String)`
- Produces: `processFile` 完成后 `Material.previewPath` 已按类型填充

- [ ] **Step 1: 注入 PdfConversionService**

在 `FileProcessingService` 字段区,`documentIngestionService` 之后加入:

```java
    private final PdfConversionService pdfConversionService;
```

- [ ] **Step 2: 在 processFile 中计算 previewPath**

将 `processFile` 中「更新状态为 READY」的代码块替换为如下(先算 previewPath,再一并保存):

```java
            // 计算预览 PDF 路径
            String previewPath = resolvePreviewPath(userId, type, filename, filePath);

            // 更新状态为 READY 并保存 previewPath
            materialRepository.findById(materialId).ifPresent(material -> {
                material.setStatus(Material.Status.READY);
                material.setPreviewPath(previewPath);
                materialRepository.save(material);
            });
```

- [ ] **Step 3: 新增 resolvePreviewPath 私有方法**

在 `FileProcessingService` 类中新增:

```java
    /**
     * 计算预览 PDF 路径:
     * pdf 直接用原文件;word/ppt/excel 转换;转换失败或其他类型返回 null。
     */
    private String resolvePreviewPath(Long userId, String type, String filename, Path filePath) {
        if ("pdf".equals(type)) {
            return filePath.toString();
        }
        if (pdfConversionService.isConvertibleType(type)) {
            try {
                return pdfConversionService.convertToPdf(filePath, filename).toString();
            } catch (Exception e) {
                log.warn("生成预览 PDF 失败,跳过预览: type={}, file={}", type, filename, e);
                return null;
            }
        }
        return null;
    }
```

- [ ] **Step 4: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/team/study/service/FileProcessingService.java
git commit -m "feat: 文件处理链路接入 PDF 预览转换"
```

---

### Task 7: MaterialResponse 增加 previewable 字段

**Files:**
- Modify: `backend/src/main/java/com/team/study/dto/response/MaterialResponse.java`
- Modify: `backend/src/main/java/com/team/study/service/MaterialServiceImpl.java`(`toResponse`)

**Interfaces:**
- Consumes: `Material.getPreviewPath()`
- Produces: `MaterialResponse` 末位新增 `boolean previewable`;`toResponse` 传入 `material.getPreviewPath() != null`

- [ ] **Step 1: 修改 MaterialResponse**

在 `MaterialResponse` 的 `createdAt` 字段之后加入:

```java
    private boolean previewable;
```

- [ ] **Step 2: 修改 MaterialServiceImpl.toResponse**

将 `toResponse` 的 `return new MaterialResponse(...)` 改为(在 `createdAt` 后补一个参数):

```java
    private MaterialResponse toResponse(Material material) {
        return new MaterialResponse(
                material.getId(),
                material.getFilename(),
                material.getType(),
                material.getStatus().name(),
                material.getCreatedAt(),
                material.getPreviewPath() != null
        );
    }
```

- [ ] **Step 3: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS(`@AllArgsConstructor` 生成的构造器已含新参数)

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/team/study/dto/response/MaterialResponse.java backend/src/main/java/com/team/study/service/MaterialServiceImpl.java
git commit -m "feat: MaterialResponse 增加 previewable 标识"
```

---

### Task 8: 预览接口 —— Service 层方法

**Files:**
- Modify: `backend/src/main/java/com/team/study/service/MaterialService.java`(接口)
- Modify: `backend/src/main/java/com/team/study/service/MaterialServiceImpl.java`(实现)
- Test: `backend/src/test/java/com/team/study/service/MaterialServiceImplTest.java`

**Interfaces:**
- Consumes: `MaterialRepository.findById`、`SecurityUtil.getCurrentUserId()`、`Material.getPreviewPath()`
- Produces: `org.springframework.core.io.Resource getPreviewResource(Long id)` —— 校验归属与 previewPath 存在,返回 `FileSystemResource`;无权抛 `IllegalArgumentException("无权访问该资料")`,无预览抛 `IllegalArgumentException("预览不可用")`

- [ ] **Step 1: 写失败测试**

在 `MaterialServiceImplTest` 增加(沿用该测试已有的 mock 风格;若已有 `materialRepository`/`SecurityUtil` 的 mock 设置则复用):

```java
@Test
void getPreviewResource_throwsWhenNoPreview() {
    // 假设 mock: 当前用户 1, material 属于用户 1 但 previewPath 为 null
    Material m = new Material();
    m.setId(10L);
    m.setUserId(1L);
    m.setPreviewPath(null);
    when(materialRepository.findById(10L)).thenReturn(java.util.Optional.of(m));

    try (var mocked = mockStatic(SecurityUtil.class)) {
        mocked.when(SecurityUtil::getCurrentUserId).thenReturn(1L);
        var ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.getPreviewResource(10L));
        assertEquals("预览不可用", ex.getMessage());
    }
}
```

> 注:若现有测试类未引入 `mockStatic`,需确保 `mockito-inline`/`mockito-core` 在 spring-boot-starter-test 中可用(3.4.4 默认含 mockito 5,支持静态 mock)。`materialService` 字段沿用测试类已有的被测实例。

- [ ] **Step 2: 运行测试,确认失败**

Run: `cd backend && ./mvnw -q -Dtest=MaterialServiceImplTest#getPreviewResource_throwsWhenNoPreview test`
Expected: FAIL(方法不存在,编译失败)

- [ ] **Step 3: 在 MaterialService 接口声明方法**

在 `MaterialService` 接口中加入:

```java
    org.springframework.core.io.Resource getPreviewResource(Long id);
```

- [ ] **Step 4: 在 MaterialServiceImpl 实现**

在 `MaterialServiceImpl` 中新增方法(放在 `delete` 之后):

```java
    @Override
    public org.springframework.core.io.Resource getPreviewResource(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资料不存在"));
        if (!material.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该资料");
        }
        String previewPath = material.getPreviewPath();
        if (previewPath == null || previewPath.isBlank()) {
            throw new IllegalArgumentException("预览不可用");
        }
        return new org.springframework.core.io.FileSystemResource(previewPath);
    }
```

- [ ] **Step 5: 运行测试,确认通过**

Run: `cd backend && ./mvnw -q -Dtest=MaterialServiceImplTest#getPreviewResource_throwsWhenNoPreview test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/team/study/service/MaterialService.java backend/src/main/java/com/team/study/service/MaterialServiceImpl.java backend/src/test/java/com/team/study/service/MaterialServiceImplTest.java
git commit -m "feat: 新增预览资源获取的 service 方法(含鉴权)"
```

---

### Task 9: 预览接口 —— Controller

**Files:**
- Modify: `backend/src/main/java/com/team/study/controller/MaterialController.java`

**Interfaces:**
- Consumes: `MaterialService.getPreviewResource(Long)`
- Produces: `GET /api/materials/{id}/preview` 返回 `ResponseEntity<Resource>`,`Content-Type: application/pdf`,`Content-Disposition: inline`

- [ ] **Step 1: 新增 preview 端点**

在 `MaterialController` 的 `delete` 方法之后加入,并补充所需 import(`org.springframework.core.io.Resource`、`org.springframework.http.*`):

```java
    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        Resource resource = materialService.getPreviewResource(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }
```

import 区加入:

```java
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
```

(`org.springframework.http.MediaType` 已存在,无需重复导入)

- [ ] **Step 2: 编译验证**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/team/study/controller/MaterialController.java
git commit -m "feat: 新增 GET /api/materials/{id}/preview 预览接口"
```

---

### Task 10: 删除资料时清理预览 PDF

**Files:**
- Modify: `backend/src/main/java/com/team/study/service/MaterialServiceImpl.java`

**Interfaces:**
- Consumes: `Material.getPreviewPath()`

- [ ] **Step 1: 在 deletePhysicalFile 中追加删除 previewPath**

将 `deletePhysicalFile` 方法体改为同时删除 storagePath 与 previewPath(previewPath 与 storagePath 相同时,`deleteIfExists` 幂等无害):

```java
    private void deletePhysicalFile(Material material) {
        deleteQuietly(material.getStoragePath());
        deleteQuietly(material.getPreviewPath());
    }

    private void deleteQuietly(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", path, e);
        }
    }
```

- [ ] **Step 2: 运行回归测试**

Run: `cd backend && ./mvnw -q -Dtest=MaterialServiceImplTest test`
Expected: PASS(现有删除相关测试不受影响)

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/team/study/service/MaterialServiceImpl.java
git commit -m "feat: 删除资料时一并清理预览 PDF"
```

---

### Task 11: 全量构建与冒烟验证

**Files:** 无(验证任务)

- [ ] **Step 1: 全量测试**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS,所有测试通过

- [ ] **Step 2: 本地冒烟(需已安装 LibreOffice)**

启动应用,登录后:
1. 上传一个 .docx → `GET /api/materials` 中该项 `previewable=true`
2. `GET /api/materials/{id}/preview` 返回 `application/pdf` 且能在浏览器/iframe 打开
3. 上传 .pptx、.xlsx 重复验证
4. 上传 .txt → `previewable=false`,调用 preview 返回「预览不可用」错误

> 若未安装 LibreOffice:转换会失败 → previewPath=null、previewable=false、status 仍 READY、应用不崩溃(符合降级要求)。

- [ ] **Step 3: 标记计划完成**

无代码改动,无需 commit。

---

## 自检结果

- **Spec 覆盖**:A 部分(白名单/类型映射/提取器)→ Task 2、3;B 部分(实体字段/转换服务/链路接入/预览接口/previewable/删除清理)→ Task 4–10;依赖 → Task 1;验证 → Task 11。全部覆盖。
- **占位符**:无 TBD/TODO,所有代码步骤含完整代码。
- **类型一致性**:`convertToPdf(Path, String)`、`isConvertibleType(String)`、`getPreviewResource(Long)`、`previewable`、`previewPath` 在定义任务与消费任务间命名一致。
- **已知前提**:本地需安装 LibreOffice 才能真正生成 PDF;未安装时按降级路径运行(已在 Task 11 说明)。
