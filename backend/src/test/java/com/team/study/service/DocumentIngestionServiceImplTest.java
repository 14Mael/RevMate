package com.team.study.service;

import com.team.study.entity.EmbeddingStatus;
import com.team.study.entity.MaterialChunk;
import com.team.study.repository.MaterialChunkRepository;
import com.team.study.repository.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIngestionServiceImplTest {

    @Test
    void ingestedChunksAreReadFromRepositoryByNewServiceInstance() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        List<MaterialChunk> persisted = new ArrayList<>();
        doAnswer(invocation -> {
            persisted.clear();
            persisted.addAll(invocation.getArgument(0));
            return persisted;
        }).when(repository).saveAll(anyList());
        when(repository.findByMaterialIdOrderByChunkIndexAsc(99L)).thenAnswer(invocation -> persisted);

        DocumentIngestionServiceImpl writer = new DocumentIngestionServiceImpl(repository, materialRepository);
        writer.ingest(7L, 99L, "os-notes.pdf", "操作系统包含进程调度。\n数据库事务有 ACID 特性。");

        DocumentIngestionServiceImpl readerAfterRestart = new DocumentIngestionServiceImpl(repository, materialRepository);
        String context = readerAfterRestart.getMaterialContext(7L, 99L, 8);

        verify(repository).deleteByMaterialId(99L);
        assertThat(context).contains("操作系统包含进程调度");
        assertThat(context).contains("数据库事务有 ACID 特性");
    }

    @Test
    void retrieveSearchesOnlyCurrentUsersPersistedChunks() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        when(repository.findByUserId(7L)).thenReturn(List.of(
                chunk(1L, 7L, 99L, 0, "os-notes.pdf", "操作系统包含进程调度。"),
                chunk(2L, 7L, 100L, 0, "db-notes.pdf", "数据库事务有 ACID 特性。")
        ));

        DocumentIngestionServiceImpl service = new DocumentIngestionServiceImpl(repository, materialRepository);
        List<Document> results = service.retrieve(7L, "进程调度", 5);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getText()).contains("进程调度");
        assertThat(results.getFirst().getMetadata()).containsEntry("materialId", "99");
    }

    @Test
    void retrieveWithinSubjectOnlySearchesSubjectMaterials() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        when(materialRepository.findIdsByUserIdAndSubjectId(7L, 3L)).thenReturn(List.of(99L));
        when(repository.findByUserIdAndMaterialIdIn(7L, List.of(99L))).thenReturn(List.of(
                chunk(1L, 7L, 99L, 0, "java-notes.pdf", "Java 封装使用 private 字段。")
        ));

        DocumentIngestionServiceImpl service = new DocumentIngestionServiceImpl(repository, materialRepository);
        List<Document> results = service.retrieve(7L, 3L, "Java 封装", 5);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getText()).contains("private");
        assertThat(results.getFirst().getMetadata()).containsEntry("materialId", "99");
    }

    @Test
    void getSubjectContextCombinesChunksFromSubjectMaterials() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        when(materialRepository.findIdsByUserIdAndSubjectId(7L, 3L)).thenReturn(List.of(99L, 100L));
        when(repository.findByUserIdAndMaterialIdInOrderByMaterialIdAscChunkIndexAsc(7L, List.of(99L, 100L)))
                .thenReturn(List.of(
                        chunk(1L, 7L, 99L, 0, "java-a.pdf", "第一段"),
                        chunk(2L, 7L, 100L, 0, "java-b.pdf", "第二段")
                ));

        DocumentIngestionServiceImpl service = new DocumentIngestionServiceImpl(repository, materialRepository);
        String context = service.getSubjectContext(7L, 3L, 8);

        assertThat(context).contains("第一段").contains("第二段");
    }

    @Test
    void materialContextForSummaryIncludesOpeningEndingAndConclusionChunks() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        when(repository.findByMaterialIdOrderByChunkIndexAsc(99L)).thenReturn(List.of(
                chunk(1L, 7L, 99L, 0, "ppt.pdf", "开头：本 PPT 介绍信息系统的核心概念。"),
                chunk(2L, 7L, 99L, 1, "ppt.pdf", "中间：这一页只介绍示例数据。"),
                chunk(3L, 7L, 99L, 2, "ppt.pdf", "结论：信息系统需要人员、流程和技术协同。"),
                chunk(4L, 7L, 99L, 3, "ppt.pdf", "结尾：后续展望包括智能化和自动化。")
        ));

        DocumentIngestionServiceImpl service = new DocumentIngestionServiceImpl(repository, materialRepository);
        String context = service.getMaterialContext(7L, 99L, "请总结这个 PPT 的主要结论", 3);

        assertThat(context).contains("开头：本 PPT");
        assertThat(context).contains("结论：信息系统需要人员、流程和技术协同。");
        assertThat(context).contains("结尾：后续展望");
        assertThat(context).doesNotContain("中间：这一页只介绍示例数据。");
    }

    @Test
    void ingestSplitsLongParagraphsIntoBoundedChunks() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        List<MaterialChunk> persisted = new ArrayList<>();
        doAnswer(invocation -> {
            persisted.addAll(invocation.getArgument(0));
            return persisted;
        }).when(repository).saveAll(anyList());
        StringBuilder transcriptBuilder = new StringBuilder();
        for (int i = 0; i < 700; i++) {
            transcriptBuilder.append("段").append(i).append("-软件测试;");
        }
        String longTranscript = transcriptBuilder.toString();

        DocumentIngestionServiceImpl service = new DocumentIngestionServiceImpl(repository, materialRepository);
        service.ingest(7L, 101L, "lecture.m4a", longTranscript);

        assertThat(persisted).hasSizeGreaterThan(1);
        assertThat(persisted)
                .allSatisfy(chunk -> assertThat(chunk.getText()).hasSizeLessThanOrEqualTo(500));
        assertThat(reconstructPossiblyOverlappedChunks(persisted))
                .isEqualTo(longTranscript);
    }

    private String reconstructPossiblyOverlappedChunks(List<MaterialChunk> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }
        StringBuilder reconstructed = new StringBuilder(chunks.getFirst().getText());
        for (int i = 1; i < chunks.size(); i++) {
            String next = chunks.get(i).getText();
            int overlap = commonBoundaryLength(reconstructed, next);
            reconstructed.append(next.substring(overlap));
        }
        return reconstructed.toString();
    }

    private int commonBoundaryLength(StringBuilder previous, String next) {
        int max = Math.min(50, Math.min(previous.length(), next.length()));
        for (int len = max; len > 0; len--) {
            int start = previous.length() - len;
            if (previous.substring(start).equals(next.substring(0, len))) {
                return len;
            }
        }
        return 0;
    }

    @Test
    void ingestStoresReadyEmbeddingWhenEmbeddingServiceSucceeds() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        List<MaterialChunk> persisted = new ArrayList<>();
        doAnswer(invocation -> {
            persisted.addAll(invocation.getArgument(0));
            return persisted;
        }).when(repository).saveAll(anyList());
        FakeEmbeddingService embeddingService = new FakeEmbeddingService(true);
        embeddingService.put("操作系统包含进程同步。", new float[]{1.0f, 0.0f});

        DocumentIngestionServiceImpl service = new DocumentIngestionServiceImpl(repository, materialRepository, embeddingService);
        service.ingest(7L, 101L, "os.pdf", "操作系统包含进程同步。");

        assertThat(persisted).hasSize(1);
        MaterialChunk chunk = persisted.getFirst();
        assertThat(chunk.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.READY);
        assertThat(chunk.getEmbeddingModel()).isEqualTo("fake-embedding");
        assertThat(EmbeddingVectorCodec.deserialize(chunk.getEmbedding())).containsExactly(1.0f, 0.0f);
    }

    @Test
    void ingestMarksEmbeddingFailedWithoutFailingUploadWhenEmbeddingFails() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        List<MaterialChunk> persisted = new ArrayList<>();
        doAnswer(invocation -> {
            persisted.addAll(invocation.getArgument(0));
            return persisted;
        }).when(repository).saveAll(anyList());
        FakeEmbeddingService embeddingService = new FakeEmbeddingService(true);
        embeddingService.failAll();

        DocumentIngestionServiceImpl service = new DocumentIngestionServiceImpl(repository, materialRepository, embeddingService);
        service.ingest(7L, 101L, "os.pdf", "操作系统包含进程同步。");

        assertThat(persisted).hasSize(1);
        MaterialChunk chunk = persisted.getFirst();
        assertThat(chunk.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.FAILED);
        assertThat(chunk.getEmbedding()).isNull();
    }

    @Test
    void retrieveUsesVectorSimilarityWhenQuestionUsesDifferentWords() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        when(materialRepository.findIdsByUserIdAndSubjectId(7L, 3L)).thenReturn(List.of(99L, 100L, 101L));
        when(repository.findByUserIdAndMaterialIdIn(7L, List.of(99L, 100L, 101L))).thenReturn(List.of(
                embeddedChunk(1L, 7L, 99L, 0, "os-sync.pdf", "进程同步用于协调并发进程对临界资源的访问。", new float[]{1.0f, 0.0f}),
                embeddedChunk(2L, 7L, 100L, 0, "os-schedule.pdf", "处理机调度负责按照调度算法选择就绪进程运行。", new float[]{0.0f, 1.0f}),
                embeddedChunk(3L, 7L, 101L, 0, "memory.pdf", "内存管理关注分页、分段和地址转换。", new float[]{0.0f, 0.0f})
        ));
        FakeEmbeddingService embeddingService = new FakeEmbeddingService(true);
        embeddingService.put("请总结并发控制和 CPU 分配的核心知识点", new float[]{1.0f, 1.0f});

        DocumentIngestionServiceImpl service = new DocumentIngestionServiceImpl(repository, materialRepository, embeddingService);
        List<Document> results = service.retrieve(7L, 3L, "请总结并发控制和 CPU 分配的核心知识点", 5);

        assertThat(results).extracting(Document::getText)
                .anySatisfy(text -> assertThat(text).contains("进程同步"))
                .anySatisfy(text -> assertThat(text).contains("处理机调度"));
        assertThat(results).extracting(Document::getText)
                .noneSatisfy(text -> assertThat(text).contains("内存管理"));
        assertThat(results).allSatisfy(document -> assertThat(document.getMetadata()).containsKey("score"));
    }

    @Test
    void retrieveFallsBackToKeywordSearchWhenQueryEmbeddingFails() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        when(repository.findByUserId(7L)).thenReturn(List.of(
                chunk(1L, 7L, 99L, 0, "os-notes.pdf", "操作系统包含进程调度。"),
                chunk(2L, 7L, 100L, 0, "db-notes.pdf", "数据库事务有 ACID 特性。")
        ));
        FakeEmbeddingService embeddingService = new FakeEmbeddingService(true);
        embeddingService.failAll();

        DocumentIngestionServiceImpl service = new DocumentIngestionServiceImpl(repository, materialRepository, embeddingService);
        List<Document> results = service.retrieve(7L, "进程调度", 5);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getText()).contains("进程调度");
    }

    @Test
    void reindexMaterialEmbedsOnlyChunksMissingReadyEmbeddings() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        MaterialChunk ready = embeddedChunk(1L, 7L, 99L, 0, "os.pdf", "已经有向量的切片。", new float[]{1.0f, 0.0f});
        MaterialChunk pending = chunk(2L, 7L, 99L, 1, "os.pdf", "进程同步用于协调临界资源访问。");
        pending.setEmbeddingStatus(EmbeddingStatus.PENDING);
        MaterialChunk failed = chunk(3L, 7L, 99L, 2, "os.pdf", "处理机调度选择就绪进程运行。");
        failed.setEmbeddingStatus(EmbeddingStatus.FAILED);
        when(repository.findByMaterialIdOrderByChunkIndexAsc(99L)).thenReturn(List.of(ready, pending, failed));
        FakeEmbeddingService embeddingService = new FakeEmbeddingService(true);
        embeddingService.put("进程同步用于协调临界资源访问。", new float[]{0.5f, 0.5f});
        embeddingService.put("处理机调度选择就绪进程运行。", new float[]{0.0f, 1.0f});

        DocumentIngestionServiceImpl service = new DocumentIngestionServiceImpl(repository, materialRepository, embeddingService);
        int updated = service.reindexMaterial(7L, 99L);

        assertThat(updated).isEqualTo(2);
        assertThat(pending.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.READY);
        assertThat(failed.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.READY);
        assertThat(ready.getEmbedding()).isEqualTo(EmbeddingVectorCodec.serialize(new float[]{1.0f, 0.0f}));
        verify(repository).saveAll(List.of(pending, failed));
    }

    @Test
    void reindexMaterialReturnsZeroWhenEmbeddingDisabled() {
        MaterialChunkRepository repository = mock(MaterialChunkRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        MaterialChunk pending = chunk(2L, 7L, 99L, 1, "os.pdf", "进程同步用于协调临界资源访问。");
        pending.setEmbeddingStatus(EmbeddingStatus.PENDING);
        when(repository.findByMaterialIdOrderByChunkIndexAsc(99L)).thenReturn(List.of(pending));

        DocumentIngestionServiceImpl service = new DocumentIngestionServiceImpl(repository, materialRepository, new FakeEmbeddingService(false));
        int updated = service.reindexMaterial(7L, 99L);

        assertThat(updated).isZero();
        verify(repository, never()).saveAll(anyList());
    }

    private MaterialChunk chunk(Long id, Long userId, Long materialId, int index, String source, String text) {
        MaterialChunk chunk = new MaterialChunk();
        chunk.setId(id);
        chunk.setUserId(userId);
        chunk.setMaterialId(materialId);
        chunk.setChunkIndex(index);
        chunk.setSource(source);
        chunk.setText(text);
        return chunk;
    }

    private MaterialChunk embeddedChunk(Long id, Long userId, Long materialId, int index, String source, String text, float[] embedding) {
        MaterialChunk chunk = chunk(id, userId, materialId, index, source, text);
        chunk.setEmbedding(EmbeddingVectorCodec.serialize(embedding));
        chunk.setEmbeddingModel("fake-embedding");
        chunk.setEmbeddingStatus(EmbeddingStatus.READY);
        return chunk;
    }

    private static class FakeEmbeddingService implements EmbeddingService {
        private final boolean enabled;
        private final java.util.Map<String, float[]> vectors = new java.util.HashMap<>();
        private boolean failAll;

        private FakeEmbeddingService(boolean enabled) {
            this.enabled = enabled;
        }

        private void put(String text, float[] vector) {
            vectors.put(text, vector);
        }

        private void failAll() {
            failAll = true;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public String modelName() {
            return "fake-embedding";
        }

        @Override
        public float[] embed(String text) {
            if (failAll) {
                throw new IllegalStateException("embedding failed");
            }
            return vectors.getOrDefault(text, new float[]{0.0f, 0.0f});
        }
    }
}
