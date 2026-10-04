package me.beiming.aichat.dto;

import lombok.Data;

@Data
public class RollbackDTO {
    private String conversationId;
    private Long timestamp;
}