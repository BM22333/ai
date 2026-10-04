package me.beiming.aichat.dto;

import lombok.Data;

@Data
public class UserApiDTO {
    private Long id;         // 配置ID（更新时必填）
    private String userId;
    private String apiKey;
    private String modelName;
    private String baseUrl;
    private String configName;
}
