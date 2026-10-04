package me.beiming.aichat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

@Data
@TableName("mcp_server")
public class McpServer {
    String userId;
    String name;
    String baseUrl;
    /** 自定义 HTTP 请求头（JSON 字符串格式: {"X-API-Key":"sk-xxx"}），可为 null */
    String headers;
    List<ToolDefinition> tools;
}
