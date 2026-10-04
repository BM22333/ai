package me.beiming.aichat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import me.beiming.aichat.dto.McpServerDTO;
import me.beiming.aichat.dto.UserIdDTO;
import me.beiming.aichat.entity.Result;
import me.beiming.aichat.entity.ToolDefinition;
import me.beiming.aichat.service.McpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 管理控制器。
 * <p>
 * 提供 MCP Server 的增删查和重连功能。
 * 后端自动完成连接建立、工具发现和注册。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@Tag(name = "MCP Server 管理", description = "MCP 远程工具服务器的配置与连接管理")
public class McpController {

    @Autowired
    private McpService mcpService;

    @Operation(
            summary = "添加 MCP Server",
            description = """
                    配置一个新的 MCP 远程工具服务器。后端会自动：

                    1. 保存配置到 MySQL

                    2. 通过 Streamable HTTP 连接到 MCP Server

                    3. 握手并协商协议版本

                    4. 发现远程工具列表

                    5. 注册到 ToolRegistry（命名格式: mcp_{name}__{toolName}）

                    成功返回已发现的工具定义列表，连接失败会自动回滚 MySQL 记录。"""
    )
    @PostMapping("/servers")
    public Result<List<ToolDefinition>> addServer(@RequestBody McpServerDTO dto) {
        String userId = dto.getUserId();
        String name = dto.getName();
        String baseUrl = dto.getBaseUrl();

        if (userId == null || userId.isEmpty()) {
            return Result.error("用户 ID 不能为空");
        }
        if (name == null || name.isBlank()) {
            return Result.error("MCP Server 名称不能为空");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return Result.error("MCP Server 地址不能为空");
        }

        return mcpService.addMcpServer(userId, name, baseUrl, dto.getHeaders());
    }

    @Operation(
            summary = "删除 MCP Server",
            description = "断开连接、注销所有工具、删除 MySQL 记录。"
    )
    @DeleteMapping("/servers/{name}")
    public Result<Void> removeServer(
            @PathVariable String name,
            @RequestBody UserIdDTO dto) {
        String userId = dto.getUserId();
        if (userId == null || userId.isEmpty()) {
            return Result.error("用户 ID 不能为空");
        }
        if (name == null || name.isBlank()) {
            return Result.error("MCP Server 名称不能为空");
        }
        return mcpService.removeMcpServer(userId, name);
    }

    @Operation(
            summary = "列出 MCP Server",
            description = "获取当前用户的所有 MCP Server 配置及连接状态。"
    )
    @PostMapping("/servers/list")
    public Result<List<Map<String, Object>>> listServers(@RequestBody UserIdDTO dto) {
        String userId = dto.getUserId();
        if (userId == null || userId.isEmpty()) {
            return Result.error("用户 ID 不能为空");
        }
        return mcpService.listMcpServers(userId);
    }

    @Operation(
            summary = "重连 MCP Server",
            description = "断开旧连接后重新连接指定的 MCP Server，重新发现并注册工具。"
    )
    @PostMapping("/servers/{name}/reconnect")
    public Result<List<ToolDefinition>> reconnect(
            @PathVariable String name,
            @RequestBody UserIdDTO dto) {
        String userId = dto.getUserId();
        if (userId == null || userId.isEmpty()) {
            return Result.error("用户 ID 不能为空");
        }
        if (name == null || name.isBlank()) {
            return Result.error("MCP Server 名称不能为空");
        }
        return mcpService.reconnectMcpServer(userId, name);
    }

    @Operation(
            summary = "获取 MCP 工具列表（按 Server 分组）",
            description = """
                    返回当前用户所有 MCP Server 下的工具，按 Server 分组展示。
                    每个工具包含 fullName（内部全名，传给 AI 时使用）和 displayName（原始名称，前端展示用）。
                    connected 字段表示该 Server 当前是否在线。"""
    )
    @PostMapping("/tools")
    public Result<List<Map<String, Object>>> getMcpTools(@RequestBody UserIdDTO dto) {
        String userId = dto.getUserId();
        if (userId == null || userId.isEmpty()) {
            return Result.error("用户 ID 不能为空");
        }
        return mcpService.getMcpToolsGrouped(userId);
    }

    @Operation(
            summary = "MCP Server 健康检查",
            description = "通过调用远端 listTools 验证指定 MCP Server 是否可达。"
    )
    @PostMapping("/servers/{name}/health")
    public Result<Map<String, Object>> healthCheck(
            @PathVariable String name,
            @RequestBody UserIdDTO dto) {
        String userId = dto.getUserId();
        if (userId == null || userId.isEmpty()) {
            return Result.error("用户 ID 不能为空");
        }
        return mcpService.healthCheckMcpServer(userId, name);
    }

    @Operation(
            summary = "删除所有 MCP Server",
            description = "断开用户所有 MCP 连接、注销所有 MCP 工具、删除所有 MySQL 配置记录。"
    )
    @DeleteMapping("/servers")
    public Result<Void> removeAllServers(@RequestBody UserIdDTO dto) {
        String userId = dto.getUserId();
        if (userId == null || userId.isEmpty()) {
            return Result.error("用户 ID 不能为空");
        }
        return mcpService.removeAllMcpServers(userId);
    }
}
