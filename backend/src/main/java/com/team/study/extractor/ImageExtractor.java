package com.team.study.extractor;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

/**
 * 图片提取器 — 调用通义千问视觉模型 qwen-vl 识别图片中的文字与内容
 */
@Component
public class ImageExtractor implements ContentExtractor {

    private final ChatClient visionChatClient;

    public ImageExtractor(ChatModel chatModel) {
        this.visionChatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public boolean supports(String contentType) {
        return "image".equals(contentType);
    }

    @Override
    public String extract(Resource file) {
        try {
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .withModel("qwen-vl-plus")
                    .build();

            // 根据文件名推断 MIME 类型
            String filename = file.getFilename();
            String mimeString = switch (filename != null ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "") {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "bmp" -> "image/bmp";
                case "webp" -> "image/webp";
                default -> "image/png";
            };
            MimeType mimeType = MimeType.valueOf(mimeString);

            // 构造多模态消息：文本 + 图片
            Media media = new Media(mimeType, file);
            UserMessage message = UserMessage.builder()
                    .text("请详细描述这张图片中的文字内容，包括文字、图表、公式等所有信息。如果包含文字，请完整转录。")
                    .media(media)
                    .build();

            String result = visionChatClient.prompt()
                    .options(options)
                    .messages(message)
                    .call()
                    .content();

            return result != null ? result : "";
        } catch (Exception e) {
            throw new RuntimeException("图片识别失败: " + file.getFilename(), e);
        }
    }
}
