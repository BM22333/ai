package me.beiming.aichat.dto;

import lombok.Data;

@Data
public class CreateConversationDTO {
    private String conversationName;
    private String systemPrompt;
    private String apiKey;
    private String model;
    private String baseUrl;
    private String userId;

    /** 展示给 AI 的最大消息数，<=0 或 null 时使用默认值 100 */
    private Integer maxMessages;

    /** 最大工具调用深度，<=0 或 null 时使用默认值 10 */
    private Integer maxToolCallDepth;

    /** 历史后提示词，紧跟历史提示词之后发给 AI */
    private String afterHistoryPrompt;

    /** AI 温度参数 (0.0-2.0)，null 时使用默认值 1.0 */
    private Double temperature;

    /** 推理力度: low/medium/high，null 时使用默认值 high */
    private String reasoningEffort;
}