package me.beiming.aichat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工具定义信息 DTO，供前端展示可用工具列表。
 */
@Data
public class ToolDefinitionDTO {
    @Schema(description = "工具名称（唯一标识）")
    private String name;

    @Schema(description = "工具功能描述")
    private String description;

    @Schema(description = "工具来源类型: BUILTIN(内置) / MCP(MCP远程)")
    private String providerType;
}
