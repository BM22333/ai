package me.beiming.aichat.dto;

import lombok.Data;

/**
 * 按 ID 操作注入提示词 DTO。
 */
@Data
public class InsertPromptIdDTO {
    /** 提示词ID（必填） */
    private Long id;
    private String userId;
}
