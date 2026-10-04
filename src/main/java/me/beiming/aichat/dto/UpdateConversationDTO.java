package me.beiming.aichat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新会话配置 DTO。
 * 所有字段均为可选，仅更新非null字段。
 */
@Data
public class UpdateConversationDTO {
    @Schema(description = "会话ID（必填）")
    private String conversationId;

    @Schema(description = "会话名称")
    private String conversationName;

    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Schema(description = "使用的模型")
    private String model;

    @Schema(description = "API Key")
    private String apiKey;

    @Schema(description = "API Base URL")
    private String baseUrl;

    @Schema(description = "工具偏好设置，JSON数组字符串。如 [\"calculator\",\"date_time\"] 启用指定工具，[] 禁用所有，null或不传不修改")
    private String toolPreferences;

    @Schema(description = "展示给 AI 的最大消息数，null或不传不修改")
    private Integer maxMessages;

    @Schema(description = "最大工具调用深度，null或不传不修改")
    private Integer maxToolCallDepth;

    @Schema(description = "历史后提示词，紧跟历史提示词之后发给 AI，null或不传不修改")
    private String afterHistoryPrompt;

    @Schema(description = "AI 温度参数 (0.0-2.0)，null或不传不修改")
    private Double temperature;

    @Schema(description = "推理力度: low/medium/high，null或不传不修改")
    private String reasoningEffort;
}
