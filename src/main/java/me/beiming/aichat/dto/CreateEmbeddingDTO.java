package me.beiming.aichat.dto;

import lombok.Data;

@Data
public class CreateEmbeddingDTO {
    private String userId;
    private String conversationId;
    private String apiKey;
    private String model;
    private String baseUrl;
}