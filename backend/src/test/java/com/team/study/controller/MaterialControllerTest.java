package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.response.MaterialResponse;
import com.team.study.service.MaterialService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialControllerTest {

    @Test
    void uploadReturnsMaterialResponse() {
        StubMaterialService materialService = new StubMaterialService();
        MaterialController controller = new MaterialController(materialService);

        MultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello world".getBytes());

        Result<MaterialResponse> result = controller.upload(file, 1L);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getFilename()).isEqualTo("notes.txt");
        assertThat(materialService.uploadCalled).isTrue();
        assertThat(materialService.lastSubjectId).isEqualTo(1L);
    }

    @Test
    void listReturnsMaterialsForSubject() {
        StubMaterialService materialService = new StubMaterialService();
        MaterialController controller = new MaterialController(materialService);

        Result<List<MaterialResponse>> result = controller.list(1L);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).hasSize(2);
        assertThat(materialService.listSubjectId).isEqualTo(1L);
    }

    @Test
    void deleteInvokesService() {
        StubMaterialService materialService = new StubMaterialService();
        MaterialController controller = new MaterialController(materialService);

        Result<Void> result = controller.delete(42L);

        assertThat(result.getCode()).isZero();
        assertThat(materialService.deletedId).isEqualTo(42L);
    }

    @Test
    void reindexReturnsUpdatedCount() {
        StubMaterialService materialService = new StubMaterialService();
        MaterialController controller = new MaterialController(materialService);

        Result<Integer> result = controller.reindex(1L);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isEqualTo(5);
        assertThat(materialService.reindexedId).isEqualTo(1L);
    }

    @Test
    void previewReturnsPdfResourceInline() {
        StubMaterialService materialService = new StubMaterialService();
        MaterialController controller = new MaterialController(materialService);

        ResponseEntity<Resource> response = controller.preview(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("inline");
        assertThat(response.getBody()).isNotNull();
        assertThat(materialService.previewId).isEqualTo(1L);
    }

    private static class StubMaterialService implements MaterialService {
        boolean uploadCalled;
        Long lastSubjectId;
        Long listSubjectId;
        Long deletedId;
        Long reindexedId;
        Long previewId;

        @Override
        public MaterialResponse upload(MultipartFile file, Long subjectId) {
            uploadCalled = true;
            lastSubjectId = subjectId;
            return new MaterialResponse(
                    1L, subjectId, file.getOriginalFilename(), "txt",
                    "READY", LocalDateTime.now(), true, "READY", "");
        }

        @Override
        public List<MaterialResponse> list(Long subjectId) {
            listSubjectId = subjectId;
            return List.of(
                    new MaterialResponse(
                            1L, subjectId, "notes.txt", "txt",
                            "READY", LocalDateTime.now(), true, "READY", ""),
                    new MaterialResponse(
                            2L, subjectId, "guide.pdf", "pdf",
                            "READY", LocalDateTime.now(), true, "READY", ""));
        }

        @Override
        public void delete(Long id) {
            deletedId = id;
        }

        @Override
        public Resource getPreviewResource(Long id) {
            previewId = id;
            return new ByteArrayResource("%PDF-1.4 fake content".getBytes());
        }

        @Override
        public int reindex(Long id) {
            reindexedId = id;
            return 5;
        }

        @Override
        public MaterialService.AudioResource getAudioResource(Long id) {
            return new MaterialService.AudioResource(
                    new ByteArrayResource("audio".getBytes()),
                    "audio/mpeg",
                    "lecture.mp3");
        }

        @Override
        public String getTranscript(Long id) {
            return "音频文字稿";
        }
    }
}
