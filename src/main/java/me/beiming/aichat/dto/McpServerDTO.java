package me.beiming.aichat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "MCP Server 配置请求")
public class McpServerDTO {
    @Schema(description = "用户 ID", example = "uuid-xxx")
    private String userId;

    @Schema(description = "MCP Server 名称（用户自定义）", example = "weather")
    private String name;

    @Schema(description = "MCP Server 的 Streamable HTTP 端点", example = "https://weather-mcp.example.com/mcp")
    private String baseUrl;

    @Schema(description = "自定义 HTTP 请求头（JSON 对象），如 {\"X-API-Key\":\"sk-xxx\"}", example = "{\"X-API-Key\":\"sk-abc123\"}")
    private Map<String, String> headers;
}
