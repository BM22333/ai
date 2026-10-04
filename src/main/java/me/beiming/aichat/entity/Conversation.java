package me.beiming.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
@TableName("conversation")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private String conversationName;
    private String model;
    private String systemPrompt;
    @JsonIgnore
    private String apiKey;
    @JsonIgnore
    private String baseUrl;
    private String toolPreferences;

    /** 展示给 AI 的最大消息数，null 时默认 100 */
    private Integer maxMessages;

    /** 最大工具调用深度，null 时默认 10 */
    private Integer maxToolCallDepth;

    /** 历史后提示词，紧跟历史提示词之后发给 AI */
    private String afterHistoryPrompt;

    /** AI 温度参数 (0.0-2.0)，null 时默认 1.0 */
    private Double temperature;

    /** 推理力度: low/medium/high，null 时默认 high */
    private String reasoningEffort;

    public Conversation(String conversationId, String conversationName, String model, String systemPrompt, String apiKey, String baseUrl) {
        this.conversationId = conversationId;
        this.conversationName = conversationName;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }
}