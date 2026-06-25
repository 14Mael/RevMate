package com.team.study.service;

import com.team.study.entity.Material;
import com.team.study.extractor.ExtractorRouter;
import com.team.study.repository.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileProcessingServiceTest {

    @Test
    void officePreviewFailureKeepsMaterialReadyAndMarksPreviewFailed() throws Exception {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        ExtractorRouter extractorRouter = mock(ExtractorRouter.class);
        DocumentIngestionService documentIngestionService = mock(DocumentIngestionService.class);
        PdfConversionService pdfConversionService = mock(PdfConversionService.class);
        FileProcessingService service = new FileProcessingService(
                materialRepository, extractorRouter, documentIngestionService, pdfConversionService);

        Path file = Files.createTempFile("notes", ".docx");
        Material material = new Material();
        material.setId(99L);
        material.setUserId(7L);
        material.setFilename("notes.docx");
        material.setType("word");
        material.setStatus(Material.Status.PROCESSING);
        when(extractorRouter.extract(eq("word"), any(Resource.class))).thenReturn("资料正文");
        when(materialRepository.findById(99L)).thenReturn(Optional.of(material));
        when(pdfConversionService.isConvertibleType("word")).thenReturn(true);
        when(pdfConversionService.convertToPdf(file, "notes.docx"))
                .thenThrow(new IllegalStateException("LibreOffice unavailable"));

        service.processFile(7L, 99L, "notes.docx", "word", file);

        verify(documentIngestionService).ingest(7L, 99L, "notes.docx", "资料正文");
        verify(materialRepository).save(material);
        assertThat(material.getStatus()).isEqualTo(Material.Status.READY);
        assertThat(material.getPreviewPath()).isNull();
        assertThat(material.getPreviewStatus()).isEqualTo(Material.PreviewStatus.FAILED);
        assertThat(material.getPreviewMessage()).contains("PDF 预览生成失败");
    }

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
        assertThat(material.getTranscript()).isEqualTo("音频文字稿");
        assertThat(material.getPreviewPath()).isNull();
        assertThat(material.getPreviewStatus()).isEqualTo(Material.PreviewStatus.READY);
        assertThat(material.getPreviewMessage()).isNull();
    }

    @Test
    void audioTranscriptionFailureMarksMaterialFailedWithRootCauseMessage() throws Exception {
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
        assertThat(material.getPreviewMessage()).contains("资料处理失败");
        assertThat(material.getPreviewMessage()).contains("语音识别失败");
    }
}
