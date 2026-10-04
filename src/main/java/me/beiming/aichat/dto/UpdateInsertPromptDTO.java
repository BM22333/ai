package me.beiming.aichat.dto;

import lombok.Data;

/**
 * 更新注入提示词 DTO，仅更新非 null 字段。
 */
@Data
public class UpdateInsertPromptDTO {
    /** 提示词ID（必填） */
    private Long id;
    private String userId;
    private String promptName;
    private String content;
    private Integer depth;
}
