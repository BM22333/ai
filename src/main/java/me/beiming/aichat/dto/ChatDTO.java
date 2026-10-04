package me.beiming.aichat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatDTO {
    @Schema(description = "用户消息")
    private String message;

    @Schema(description = "工具调用结果")
    private String result;

    @Schema(description = "会话ID")
    private String conversationId;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "工具定义列表，JSON格式的工具数组（可选，不传则使用服务端注册的默认工具）")
    private List<Map<String, Object>> tools;

    @Schema(description = "是否启用工具调用。true=启用，false=禁用")
    private Boolean enableTools;

    @Schema(description = "指定启用的工具名称列表。null或空=不使用工具")
    private List<String> toolNames;

    @Schema(description = "本次请求要注入的提示词ID列表（来自 /ai/insertPrompt 管理），按各自记录的 depth 注入到历史中间")
    private List<Long> insertPromptIds;
}
