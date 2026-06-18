package com.team.study.service;

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
}
