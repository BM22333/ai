package me.beiming.aichat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class EditMessageDTO {
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conversationId;

    @Schema(description = "消息索引（0-based，从0开始）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int messageIndex;

    @Schema(description = "新的消息内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newContent;
}
