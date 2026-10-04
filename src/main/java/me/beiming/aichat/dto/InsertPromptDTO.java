package me.beiming.aichat.dto;

import lombok.Data;

/**
 * 创建注入提示词 DTO。
 */
@Data
public class InsertPromptDTO {
    private String userId;
    private String promptName;
    private String content;

    /** 注入深度，null 时取 0 */
    private Integer depth;
}
