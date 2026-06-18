package com.team.study.service;

import com.team.study.entity.Material;
import com.team.study.extractor.ExtractorRouter;
import com.team.study.repository.MaterialRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceImplTest {

    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private ExtractorRouter extractorRouter;
    @Mock
    private DocumentIngestionService documentIngestionService;
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
}
