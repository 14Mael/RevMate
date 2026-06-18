package com.team.study.extractor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

/**
 * 音频提取器 — 调用多模态模型将语音资料转写为文字稿。
 */
@Component
public class AudioExtractor implements ContentExtractor {

    private final ChatClient audioChatClient;

    public AudioExtractor(ChatModel chatModel) {
        this.audioChatClient = chatModel == null ? null : ChatClient.builder(chatModel).build();
    }

    @Override
    public boolean supports(String contentType) {
        return "audio".equals(contentType);
    }

    @Override
    public String extract(Resource file) {
        if (audioChatClient == null) {
            throw new IllegalStateException("语音识别模型未配置");
        }
        if (file == null) {
            throw new IllegalArgumentException("音频文件不能为空");
        }

        try {
            MimeType mimeType = MimeType.valueOf(resolveMimeType(file.getFilename()));
            Media media = new Media(mimeType, file);
            UserMessage message = UserMessage.builder()
                    .text("请将这段音频完整转写为中文文字稿。如果音频中包含英文或术语，请保留原文。只输出文字稿，不要添加解释。")
                    .media(media)
                    .build();

            String result = audioChatClient.prompt()
                    .messages(message)
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
