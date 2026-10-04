package me.beiming.aichat.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量隐藏/取消隐藏消息 DTO。
 */
@Data
public class MessageVisibilityDTO {
    private String conversationId;

    /** 消息索引列表（0-based），可重复，服务端去重 */
    private List<Integer> messageIndexes;
}
