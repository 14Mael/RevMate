# Voice Material Audio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add audio material uploads that are transcribed to text and ingested into the existing RAG and quiz knowledge base, without supporting preview.

**Architecture:** Reuse the existing upload and async processing pipeline. Add `audio` to MIME/type mapping and extractor routing, then add a focused `AudioExtractor` that sends the audio file to the configured multimodal chat model and returns a transcript string. Keep preview behavior unchanged by leaving `previewPath` null and `previewStatus=NONE` for audio.

**Tech Stack:** Spring Boot 3.4, Java 21, Spring AI `ChatClient`, Apache Tika, JUnit 5, Mockito, AssertJ.

---

## File Structure

- Modify: `backend/src/main/java/com/team/study/service/MaterialServiceImpl.java`
  - Owns upload validation, MIME detection fallback, internal type mapping, and initial preview state.
- Modify: `backend/src/main/java/com/team/study/extractor/ExtractorRouter.java`
  - Owns mapping internal material types to `ContentExtractor` implementations.
- Create: `backend/src/main/java/com/team/study/extractor/AudioExtractor.java`
  - Owns audio-to-text transcription through the configured AI chat model.
- Modify: `backend/src/test/java/com/team/study/service/MaterialServiceImplTest.java`
  - Covers audio MIME/type mapping and upload initial preview state.
- Modify: `backend/src/test/java/com/team/study/service/FileProcessingServiceTest.java`
  - Covers successful audio processing and failed audio transcription status.
- Create: `backend/src/test/java/com/team/study/extractor/AudioExtractorTest.java`
  - Covers `AudioExtractor.supports(...)` and transcript validation behavior.
- Create: `backend/src/test/java/com/team/study/extractor/ExtractorRouterTest.java`
  - Covers routing `audio` to the audio extractor.

---

### Task 1: Material Upload Accepts Audio

**Files:**
- Modify: `backend/src/test/java/com/team/study/service/MaterialServiceImplTest.java`
- Modify: `backend/src/main/java/com/team/study/service/MaterialServiceImpl.java`

- [ ] **Step 1: Write failing MIME mapping tests**

Add these tests to `MaterialServiceImplTest`:

```java
@Test
void mimeTypeToContentType_mapsAudio() {
    assertEquals("audio", ReflectionTestUtils.invokeMethod(
            materialService, "mimeTypeToContentType", "audio/mpeg"));
    assertEquals("audio", ReflectionTestUtils.invokeMethod(
            materialService, "mimeTypeToContentType", "audio/wav"));
    assertEquals("audio", ReflectionTestUtils.invokeMethod(
            materialService, "mimeTypeToContentType", "audio/webm"));
}

@Test
void detectMimeType_fallsBackForAudioExtensions() throws Exception {
    Path missing = uploadRoot.resolve("lecture.mp3");

    assertEquals("audio/mpeg", ReflectionTestUtils.invokeMethod(
            materialService, "detectMimeType", missing.toFile(), "lecture.mp3"));
    assertEquals("audio/wav", ReflectionTestUtils.invokeMethod(
            materialService, "detectMimeType", missing.toFile(), "lecture.wav"));
    assertEquals("audio/mp4", ReflectionTestUtils.invokeMethod(
            materialService, "detectMimeType", missing.toFile(), "lecture.m4a"));
    assertEquals("audio/webm", ReflectionTestUtils.invokeMethod(
            materialService, "detectMimeType", missing.toFile(), "lecture.webm"));
    assertEquals("audio/ogg", ReflectionTestUtils.invokeMethod(
            materialService, "detectMimeType", missing.toFile(), "lecture.ogg"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=MaterialServiceImplTest test
```

Expected: `mimeTypeToContentType_mapsAudio` fails because audio maps to `unknown`; extension fallback test fails because audio extensions map to `application/octet-stream`.

- [ ] **Step 3: Implement minimal MIME/type mapping**

In `MaterialServiceImpl`, update `ALLOWED_MIME_TYPES`:

```java
private static final List<String> ALLOWED_MIME_TYPES = List.of(
        "text/plain",
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel",
        "image/",
        "audio/"
);
```

In `detectMimeType(...)`, add extension fallback cases:

```java
case "mp3" -> "audio/mpeg";
case "wav" -> "audio/wav";
case "m4a" -> "audio/mp4";
case "webm" -> "audio/webm";
case "ogg" -> "audio/ogg";
```

In `mimeTypeToContentType(...)`, add:

```java
if (mimeType.startsWith("audio/")) return "audio";
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=MaterialServiceImplTest test
```

Expected: `MaterialServiceImplTest` passes.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/team/study/service/MaterialServiceImpl.java backend/src/test/java/com/team/study/service/MaterialServiceImplTest.java
git commit -m "feat: accept audio material uploads"
```

---

### Task 2: Audio Materials Keep Preview Disabled

**Files:**
- Modify: `backend/src/test/java/com/team/study/service/FileProcessingServiceTest.java`
- Modify: `backend/src/main/java/com/team/study/service/FileProcessingService.java`

- [ ] **Step 1: Write failing audio preview test**

Add this test to `FileProcessingServiceTest`:

```java
@Test
void audioProcessingKeepsPreviewDisabledAndMarksReady() throws Exception {
    MaterialRepository materialRepository = mock(MaterialRepository.class);
    ExtractorRouter extractorRouter = mock(ExtractorRouter.class);
    DocumentIngestionService documentIngestionService = mock(DocumentIngestionService.class);
    PdfConversionService pdfConversionService = mock(PdfConversionService.class);
    FileProcessingService service = new FileProcessingService(
            materialRepository, extractorRouter, documentIngestionService, pdfConversionService);

    Path file = Files.createTempFile("lecture", ".mp3");
    Material material = new Material();
    material.setId(100L);
    material.setUserId(7L);
    material.setFilename("lecture.mp3");
    material.setType("audio");
    material.setStatus(Material.Status.PROCESSING);
    when(extractorRouter.extract(eq("audio"), any(Resource.class))).thenReturn("音频文字稿");
    when(materialRepository.findById(100L)).thenReturn(Optional.of(material));

    service.processFile(7L, 100L, "lecture.mp3", "audio", file);

    verify(documentIngestionService).ingest(7L, 100L, "lecture.mp3", "音频文字稿");
    verify(materialRepository).save(material);
    assertThat(material.getStatus()).isEqualTo(Material.Status.READY);
    assertThat(material.getPreviewPath()).isNull();
    assertThat(material.getPreviewStatus()).isEqualTo(Material.PreviewStatus.NONE);
    assertThat(material.getPreviewMessage()).isNull();
}
```

- [ ] **Step 2: Run test to verify current behavior**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=FileProcessingServiceTest#audioProcessingKeepsPreviewDisabledAndMarksReady test
```

Expected: If the test already passes, keep it as regression coverage because current `resolvePreview` default branch is already correct.

- [ ] **Step 3: Write failing transcription failure test**

Add this test to `FileProcessingServiceTest`:

```java
@Test
void audioTranscriptionFailureMarksMaterialFailed() throws Exception {
    MaterialRepository materialRepository = mock(MaterialRepository.class);
    ExtractorRouter extractorRouter = mock(ExtractorRouter.class);
    DocumentIngestionService documentIngestionService = mock(DocumentIngestionService.class);
    PdfConversionService pdfConversionService = mock(PdfConversionService.class);
    FileProcessingService service = new FileProcessingService(
            materialRepository, extractorRouter, documentIngestionService, pdfConversionService);

    Path file = Files.createTempFile("lecture", ".mp3");
    Material material = new Material();
    material.setId(101L);
    material.setUserId(7L);
    material.setFilename("lecture.mp3");
    material.setType("audio");
    material.setStatus(Material.Status.PROCESSING);
    when(extractorRouter.extract(eq("audio"), any(Resource.class)))
            .thenThrow(new RuntimeException("语音识别失败"));
    when(materialRepository.findById(101L)).thenReturn(Optional.of(material));

    service.processFile(7L, 101L, "lecture.mp3", "audio", file);

    verify(documentIngestionService, never()).ingest(any(), any(), any(), any());
    verify(materialRepository).save(material);
    assertThat(material.getStatus()).isEqualTo(Material.Status.FAILED);
    assertThat(material.getPreviewStatus()).isEqualTo(Material.PreviewStatus.FAILED);
    assertThat(material.getPreviewMessage()).isEqualTo("资料处理失败，未生成预览");
}
```

- [ ] **Step 4: Run tests**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=FileProcessingServiceTest test
```

Expected: tests pass, or only compile fails if `never()` / `any()` imports are missing.

- [ ] **Step 5: Add missing Mockito imports only if needed**

If compile fails, add static imports:

```java
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
```

- [ ] **Step 6: Run tests again**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=FileProcessingServiceTest test
```

Expected: `FileProcessingServiceTest` passes.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/test/java/com/team/study/service/FileProcessingServiceTest.java backend/src/main/java/com/team/study/service/FileProcessingService.java
git commit -m "test: cover audio processing preview state"
```

---

### Task 3: Add Audio Extractor

**Files:**
- Create: `backend/src/test/java/com/team/study/extractor/AudioExtractorTest.java`
- Create: `backend/src/main/java/com/team/study/extractor/AudioExtractor.java`

- [ ] **Step 1: Write failing extractor tests**

Create `AudioExtractorTest.java`:

```java
package com.team.study.extractor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioExtractorTest {

    @Test
    void supportsAudioOnly() {
        AudioExtractor extractor = new AudioExtractor(null);

        assertTrue(extractor.supports("audio"));
        assertFalse(extractor.supports("image"));
        assertFalse(extractor.supports("pdf"));
    }

    @Test
    void extractRejectsMissingChatModel() {
        AudioExtractor extractor = new AudioExtractor(null);

        assertThrows(IllegalStateException.class, () -> extractor.extract(null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=AudioExtractorTest test
```

Expected: compilation fails because `AudioExtractor` does not exist.

- [ ] **Step 3: Implement audio extractor**

Create `AudioExtractor.java`:

```java
package com.team.study.extractor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

@Component
public class AudioExtractor implements ContentExtractor {

    private final ChatClient chatClient;

    public AudioExtractor(ChatModel chatModel) {
        this.chatClient = chatModel == null ? null : ChatClient.builder(chatModel).build();
    }

    @Override
    public boolean supports(String contentType) {
        return "audio".equals(contentType);
    }

    @Override
    public String extract(Resource file) {
        if (chatClient == null) {
            throw new IllegalStateException("语音识别模型未配置");
        }
        if (file == null) {
            throw new IllegalArgumentException("音频文件不能为空");
        }
        try {
            String result = chatClient.prompt()
                    .user(user -> user
                            .text("请将这段音频完整转写为中文文字稿。如果音频中包含英文或术语，请保留原文。只输出文字稿，不要添加解释。")
                            .media(MimeType.valueOf(resolveMimeType(file.getFilename())), file))
                    .call()
                    .content();
            if (result == null || result.isBlank()) {
                throw new IllegalStateException("语音识别结果为空");
            }
            return result.trim();
        } catch (Exception e) {
            throw new RuntimeException("语音识别失败: " + file.getFilename(), e);
        }
    }

    private String resolveMimeType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "audio/mpeg";
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            case "webm" -> "audio/webm";
            case "ogg" -> "audio/ogg";
            default -> "audio/mpeg";
        };
    }
}
```

- [ ] **Step 4: Run extractor tests**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=AudioExtractorTest test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/team/study/extractor/AudioExtractor.java backend/src/test/java/com/team/study/extractor/AudioExtractorTest.java
git commit -m "feat: add audio extractor"
```

---

### Task 4: Route Audio To AudioExtractor

**Files:**
- Create: `backend/src/test/java/com/team/study/extractor/ExtractorRouterTest.java`
- Modify: `backend/src/main/java/com/team/study/extractor/ExtractorRouter.java`
- Modify: `backend/src/test/java/com/team/study/extractor/TikaExtractorTest.java`

- [ ] **Step 1: Write failing router test**

Create `ExtractorRouterTest.java`:

```java
package com.team.study.extractor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtractorRouterTest {

    @Test
    void routesAudioToAudioExtractor() {
        ContentExtractor audioExtractor = mock(ContentExtractor.class);
        when(audioExtractor.supports("audio")).thenReturn(true);
        ExtractorRouter router = new ExtractorRouter(List.of(audioExtractor));

        router.init();

        assertThat(router.getExtractor("audio")).isSameAs(audioExtractor);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=ExtractorRouterTest test
```

Expected: fails with `不支持的文件类型: audio` because the router's known type list does not include `audio`.

- [ ] **Step 3: Add audio to router known types**

In `ExtractorRouter.init()`, change the type list to:

```java
for (String type : List.of("txt", "pdf", "word", "image", "ppt", "excel", "audio")) {
```

- [ ] **Step 4: Run router test**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=ExtractorRouterTest test
```

Expected: `ExtractorRouterTest` passes.

- [ ] **Step 5: Keep Tika from claiming audio**

Update `TikaExtractorTest.supports_rejectsUnknownTypes` expectation so it still asserts:

```java
assertFalse(extractor.supports("audio"));
```

No production code change should be needed because `TikaExtractor` already rejects audio.

- [ ] **Step 6: Run extractor routing tests**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=ExtractorRouterTest,TikaExtractorTest,AudioExtractorTest test
```

Expected: all selected extractor tests pass.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/team/study/extractor/ExtractorRouter.java backend/src/test/java/com/team/study/extractor/ExtractorRouterTest.java backend/src/test/java/com/team/study/extractor/TikaExtractorTest.java
git commit -m "feat: route audio materials to extractor"
```

---

### Task 5: Frontend Upload Accepts Audio

**Files:**
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: Update file input accept list**

In `App.vue`, change the upload input to:

```vue
<input
  type="file"
  accept=".txt,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,image/*,audio/*"
  @change="onFileChange"
/>
```

- [ ] **Step 2: Run frontend type check**

Run:

```powershell
cd frontend
npm run build
```

Expected: build passes.

- [ ] **Step 3: Commit**

```powershell
git add frontend/src/App.vue
git commit -m "feat: allow audio selection in uploader"
```

---

### Task 6: Final Verification

**Files:**
- Verify all modified backend and frontend files.

- [ ] **Step 1: Run backend tests**

Run:

```powershell
cd backend
.\mvnw.cmd -q test
```

Expected: Maven exits 0 with all backend tests passing.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: Vite build exits 0.

- [ ] **Step 3: Check git status**

Run:

```powershell
git status --short
```

Expected: only intentional changes are present.

---

## Self-Review

- Spec coverage: upload acceptance, MIME fallback, `audio` type mapping, `AudioExtractor`, router mapping, ingestion through `FileProcessingService`, disabled preview, failure status, and frontend file selection are covered.
- Placeholder scan: no implementation placeholders remain in the plan; code steps include concrete tests and concrete implementation snippets.
- Type consistency: internal type is consistently `audio`; preview statuses use `Material.PreviewStatus.NONE` and existing `FAILED`; extractor interface remains `ContentExtractor.supports(String)` and `extract(Resource)`.
