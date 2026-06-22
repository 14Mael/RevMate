package com.team.study.service;

import com.team.study.entity.Material;
import com.team.study.extractor.ExtractorRouter;
import com.team.study.repository.MaterialRepository;
import com.team.study.repository.SubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team.study.security.SecurityUtil;
import org.mockito.MockedStatic;

@ExtendWith(MockitoExtension.class)
class MaterialServiceImplTest {

    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private ExtractorRouter extractorRouter;
    @Mock
    private DocumentIngestionService documentIngestionService;
    @Mock
    private FileProcessingService fileProcessingService;
    @InjectMocks
    private MaterialServiceImpl materialService;

    @TempDir
    Path uploadRoot;

    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
        ReflectionTestUtils.setField(materialService, "uploadDir", uploadRoot.toString());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Material storedMaterial(Long id, Long userId, Path file) {
        Material m = new Material();
        m.setId(id);
        m.setUserId(userId);
        m.setFilename("doc.txt");
        m.setType("txt");
        m.setStatus(Material.Status.READY);
        m.setStoragePath(file.toString());
        return m;
    }

    @Test
    void deleteRemovesDbRecordAndPhysicalFile() throws Exception {
        loginAs(5L);
        Path file = uploadRoot.resolve("5").resolve("abc_doc.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content");
        Material m = storedMaterial(10L, 5L, file);
        when(materialRepository.findById(10L)).thenReturn(Optional.of(m));

        materialService.delete(10L);

        verify(materialRepository).delete(m);
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void deleteRejectsOtherUsersMaterialAndKeepsFile() throws Exception {
        loginAs(5L);
        Path file = uploadRoot.resolve("9").resolve("abc_doc.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content");
        Material m = storedMaterial(11L, 9L, file);
        when(materialRepository.findById(11L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> materialService.delete(11L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(materialRepository, never()).delete(m);
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void reindexDelegatesForOwnedMaterial() throws Exception {
        loginAs(5L);
        Path file = uploadRoot.resolve("5").resolve("abc_doc.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content");
        Material m = storedMaterial(10L, 5L, file);
        when(materialRepository.findById(10L)).thenReturn(Optional.of(m));
        when(documentIngestionService.reindexMaterial(5L, 10L)).thenReturn(3);

        int updated = materialService.reindex(10L);

        assertThat(updated).isEqualTo(3);
        verify(documentIngestionService).reindexMaterial(5L, 10L);
    }

    @Test
    void reindexRejectsOtherUsersMaterial() throws Exception {
        loginAs(5L);
        Path file = uploadRoot.resolve("9").resolve("abc_doc.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content");
        Material m = storedMaterial(11L, 9L, file);
        when(materialRepository.findById(11L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> materialService.reindex(11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无权访问该资料");
        verify(documentIngestionService, never()).reindexMaterial(5L, 11L);
    }

    @Test
    void mimeTypeToContentType_mapsPptAndExcel() {
        assertEquals("ppt", ReflectionTestUtils.invokeMethod(
                materialService, "mimeTypeToContentType",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        assertEquals("ppt", ReflectionTestUtils.invokeMethod(
                materialService, "mimeTypeToContentType",
                "application/vnd.ms-powerpoint"));
        assertEquals("excel", ReflectionTestUtils.invokeMethod(
                materialService, "mimeTypeToContentType",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertEquals("excel", ReflectionTestUtils.invokeMethod(
                materialService, "mimeTypeToContentType",
                "application/vnd.ms-excel"));
    }

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
    void isAllowedMimeType_acceptsAudio() {
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(
                materialService, "isAllowedMimeType", "audio/mpeg")).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(
                materialService, "isAllowedMimeType", "audio/wav")).isTrue();
    }

    @Test
    void detectMimeType_fallsBackForAudioExtensions() {
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

    @Test
    void detectMimeType_prefersKnownAudioExtension() throws Exception {
        Path m4a = uploadRoot.resolve("lecture.m4a");
        Files.writeString(m4a, "fake m4a content that tika may classify as text");

        assertEquals("audio/mp4", ReflectionTestUtils.invokeMethod(
                materialService, "detectMimeType", m4a.toFile(), "lecture.m4a"));
    }

    @Test
    void getPreviewResource_throwsWhenNoPreview() throws Exception {
        Path file = uploadRoot.resolve("5").resolve("abc_doc.docx");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content");

        Material m = storedMaterial(10L, 5L, file);
        m.setPreviewPath(null);
        when(materialRepository.findById(10L)).thenReturn(Optional.of(m));

        try (MockedStatic<SecurityUtil> securityMock = mockStatic(SecurityUtil.class)) {
            securityMock.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            assertThatThrownBy(() -> materialService.getPreviewResource(10L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("预览不可用");
        }
    }

    @Test
    void getPreviewResource_throwsWhenWrongUser() throws Exception {
        Path file = uploadRoot.resolve("9").resolve("abc_doc.docx");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content");

        Material m = storedMaterial(11L, 9L, file);
        m.setPreviewPath(file.toString());
        when(materialRepository.findById(11L)).thenReturn(Optional.of(m));

        try (MockedStatic<SecurityUtil> securityMock = mockStatic(SecurityUtil.class)) {
            securityMock.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            assertThatThrownBy(() -> materialService.getPreviewResource(11L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("无权访问该资料");
        }
    }

    @Test
    void getPreviewResource_returnsResourceWhenOk() throws Exception {
        Path file = uploadRoot.resolve("5").resolve("preview.pdf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "pdf content");

        Material m = storedMaterial(10L, 5L, file);
        m.setPreviewPath(file.toString());
        when(materialRepository.findById(10L)).thenReturn(Optional.of(m));

        try (MockedStatic<SecurityUtil> securityMock = mockStatic(SecurityUtil.class)) {
            securityMock.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            var resource = materialService.getPreviewResource(10L);
            assertThat(resource).isNotNull();
            assertThat(resource.exists()).isTrue();
        }
    }

    @Test
    void listReturnsPreviewStatusAndMessage() {
        loginAs(5L);
        Material m = storedMaterial(10L, 5L, uploadRoot.resolve("5").resolve("abc_doc.docx"));
        m.setSubjectId(7L);
        m.setPreviewStatus(Material.PreviewStatus.FAILED);
        m.setPreviewMessage("PDF 预览生成失败，请确认已安装 LibreOffice");
        when(subjectRepository.existsByIdAndUserId(7L, 5L)).thenReturn(true);
        when(materialRepository.findByUserIdAndSubjectIdOrderByCreatedAtDesc(5L, 7L)).thenReturn(List.of(m));

        try (MockedStatic<SecurityUtil> securityMock = mockStatic(SecurityUtil.class)) {
            securityMock.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            var list = materialService.list(7L);

            assertThat(list).hasSize(1);
            assertThat(list.getFirst().getSubjectId()).isEqualTo(7L);
            assertThat(list.getFirst().getPreviewStatus()).isEqualTo("FAILED");
            assertThat(list.getFirst().getPreviewMessage()).contains("LibreOffice");
            assertThat(list.getFirst().isPreviewable()).isFalse();
        }
    }

    @Test
    void listRejectsSubjectOwnedByAnotherUser() {
        loginAs(5L);
        when(subjectRepository.existsByIdAndUserId(7L, 5L)).thenReturn(false);

        try (MockedStatic<SecurityUtil> securityMock = mockStatic(SecurityUtil.class)) {
            securityMock.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            assertThatThrownBy(() -> materialService.list(7L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("学科不存在或无权访问");
        }
    }

    @Test
    void uploadRejectsMissingSubject() {
        loginAs(5L);

        assertThatThrownBy(() -> materialService.upload(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subjectId 不能为空");
    }
}
