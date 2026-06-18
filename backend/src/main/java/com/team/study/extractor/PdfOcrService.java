package com.team.study.extractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF OCR 服务 — 调用视觉模型识别扫描版 PDF 中的文字
 * 不需要安装任何外部软件
 */
@Service
public class PdfOcrService {

    private static final Logger log = LoggerFactory.getLogger(PdfOcrService.class);

    private final ChatClient visionChatClient;

    public PdfOcrService(ChatModel chatModel) {
        this.visionChatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * 识别 PDF 中的文字
     * @param pdfPath PDF 文件路径
     * @return 提取的文本
     */
    public String extractTextFromPdf(Path pdfPath) {
        try {
            // 1. PDFBox 渲染第一页为图片
            byte[] imageBytes = renderFirstPageAsImage(pdfPath);
            if (imageBytes == null) {
                return "";
            }

            // 2. 调用视觉模型识别
            MimeType mimeType = MimeType.valueOf("image/png");
            Media media = new Media(mimeType, new ByteArrayResource(imageBytes));
            UserMessage message = UserMessage.builder()
                    .text("请完整提取这张图片中的所有文字内容，包括标题、正文、表格、页眉页脚等。直接输出文字，不要加额外说明。")
                    .media(media)
                    .build();

            String result = visionChatClient.prompt()
                    .messages(message)
                    .call()
                    .content();

            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.error("PDF OCR 识别失败: {}", pdfPath, e);
            return "";
        }
    }

    /**
     * 使用 PDFBox 将 PDF 第一页渲染为 PNG 图片字节
     */
    private byte[] renderFirstPageAsImage(Path pdfPath) {
        try {
            byte[] pdfBytes = Files.readAllBytes(pdfPath);
            org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfBytes);

            try {
                if (document.getNumberOfPages() == 0) {
                    return null;
                }

                // 渲染第一页（PDFBox 3.x API）
                org.apache.pdfbox.rendering.PDFRenderer renderer =
                        new org.apache.pdfbox.rendering.PDFRenderer(document);
                BufferedImage image = renderer.renderImageWithDPI(0, 200); // 200 DPI

                // 转 PNG 字节
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                return baos.toByteArray();
            } finally {
                document.close();
            }
        } catch (Exception e) {
            log.error("PDF 渲染失败: {}", pdfPath, e);
            return null;
        }
    }
}
