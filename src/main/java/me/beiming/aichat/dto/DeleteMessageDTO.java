package me.beiming.aichat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class DeleteMessageDTO {
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conversationId;

    @Schema(description = "消息索引（0-based，从0开始）。仅删除该条消息，不影响其他消息。",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int messageIndex;
}
