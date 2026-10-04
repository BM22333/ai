package me.beiming.aichat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 模型信息")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelInfo {
    @Schema(description = "模型 ID，用于选择模型和发起聊天时指定", example = "deepseek-ai/DeepSeek-V3")
    private String id;

    @Schema(description = "对象类型，固定为 model")
    private String object;

    @Schema(description = "模型创建时间戳（秒）")
    private Long created;

    @Schema(description = "模型所属组织", example = "deepseek-ai")
    private String owned_by;
}
