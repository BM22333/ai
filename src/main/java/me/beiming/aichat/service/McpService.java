package me.beiming.aichat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import me.beiming.aichat.entity.McpServer;
import me.beiming.aichat.entity.Result;
import me.beiming.aichat.entity.ToolDefinition;
import me.beiming.aichat.mapper.McpServerMapper;
import me.beiming.aichat.toolCalling.McpServerManager;
import me.beiming.aichat.toolCalling.ToolRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * MCP Server 业务服务。
 * <p>
 * 负责 MCP Server 的增删查、重连、健康检查、工具分组查询等业务逻辑。
 * 从 UserService 中拆分出来，遵循单一职责原则。
 * </p>
 */
@Slf4j
@Service
public class McpService {

    @Autowired
    private McpServerMapper mcpServerMapper;

    @Autowired
    private McpServerManager mcpServerManager;

    @Autowired
    private ToolRegistry toolRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 添加 MCP Server 配置。
     * 流程：校验 → 检查重复 → 建立连接 → 发现工具 → 持久化到 MySQL
     *
     * @param userId  用户 ID
     * @param mcpName MCP Server 名称（用户自定义）
     * @param baseUrl MCP Server 的 Streamable HTTP 端点
     * @return 发现的工具定义列表
     */
    public Result<List<ToolDefinition>> addMcpServer(String userId, String mcpName, String baseUrl,
                                                       Map<String, String> headers) {
        // 1. 参数校验
        if (mcpName == null || mcpName.isBlank()) {
            return Result.error("MCP Server 名称不能为空");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return Result.error("MCP Server 地址不能为空");
        }

        // 2. 检查同名 Server 是否已存在
        QueryWrapper<McpServer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("name", mcpName);
        McpServer existing = mcpServerMapper.selectOne(queryWrapper);
        if (existing != null) {
            return Result.error("MCP Server \"" + mcpName + "\" 已存在，请先删除再重新添加");
        }

        // 3. 建立连接并发现工具
        List<ToolDefinition> tools;
        try {
            tools = mcpServerManager.connect(userId, mcpName, baseUrl, headers);

            // 4. 连接成功则持久化到数据库
            McpServer server = new McpServer();
            server.setUserId(userId);
            server.setName(mcpName);
            server.setBaseUrl(baseUrl);
            server.setHeaders(headersToJson(headers));
            mcpServerMapper.insert(server);
        } catch (McpServerManager.McpConnectionException e) {
            return Result.error(e.getMessage());
        } catch (DuplicateKeyException e) {
            // MySQL 写入冲突 → 回滚已建立的连接
            mcpServerManager.disconnect(userId, mcpName);
            log.warn("MCP Server 重复添加: userId={}, name={}", userId, mcpName);
            return Result.error("MCP Server \"" + mcpName + "\" 已存在，请先删除再重新添加");
        } catch (Exception e) {
            // 其他异常（如 MySQL 写入失败）→ 回滚连接
            mcpServerManager.disconnect(userId, mcpName);
            log.error("添加 MCP Server 时发生未知错误: userId={}, name={}", userId, mcpName, e);
            return Result.error("添加失败: " + e.getMessage());
        }

        log.info("用户 {} 添加 MCP Server 成功: name={}, baseUrl={}, tools={}, hasAuth={}",
                userId, mcpName, baseUrl, tools.size(), headers != null && !headers.isEmpty());
        return Result.success(tools);
    }

    /**
     * 删除 MCP Server 配置。
     * 断开连接、注销工具、删除 MySQL 记录。
     */
    public Result<Void> removeMcpServer(String userId, String mcpName) {
        QueryWrapper<McpServer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("name", mcpName);
        McpServer server = mcpServerMapper.selectOne(queryWrapper);
        if (server == null) {
            return Result.error("MCP Server \"" + mcpName + "\" 不存在");
        }

        // 1. 断开连接（含工具注销）
        mcpServerManager.disconnect(userId, mcpName);

        // 2. 删除 MySQL 记录
        mcpServerMapper.delete(queryWrapper);

        log.info("用户 {} 删除 MCP Server: name={}", userId, mcpName);
        return Result.success();
    }

    /**
     * 列出用户所有 MCP Server 配置（含连接状态）。
     */
    public Result<List<Map<String, Object>>> listMcpServers(String userId) {
        QueryWrapper<McpServer> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        List<McpServer> servers = mcpServerMapper.selectList(wrapper);

        Set<String> connectedNames = mcpServerManager.getConnectedServers(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (McpServer s : servers) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", s.getName());
            item.put("baseUrl", s.getBaseUrl());
            item.put("connected", connectedNames.contains(s.getName()));
            item.put("hasAuth", s.getHeaders() != null && !s.getHeaders().isBlank());
            item.put("headers", maskHeaders(jsonToHeaders(s.getHeaders())));
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 重连 MCP Server。
     * 从 MySQL 读取配置，重新建立连接并发现工具。
     */
    public Result<List<ToolDefinition>> reconnectMcpServer(String userId, String mcpName) {
        QueryWrapper<McpServer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("name", mcpName);
        McpServer server = mcpServerMapper.selectOne(queryWrapper);
        if (server == null) {
            return Result.error("MCP Server \"" + mcpName + "\" 不存在");
        }

        // 先断开旧连接
        mcpServerManager.disconnect(userId, mcpName);

        // 重新连接
        try {
            Map<String, String> headers = jsonToHeaders(server.getHeaders());
            List<ToolDefinition> tools = mcpServerManager.connect(
                    userId, mcpName, server.getBaseUrl(), headers);
            log.info("用户 {} 重连 MCP Server 成功: name={}, tools={}",
                    userId, mcpName, tools.size());
            return Result.success(tools);
        } catch (McpServerManager.McpConnectionException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取 MCP 工具列表，按 Server 分组（供前端工具选择界面使用）。
     * <p>
     * 返回结构中：
     * <ul>
     *   <li>{@code serverName} — 用户定义的 Server 名称</li>
     *   <li>{@code connected} — 当前是否在线</li>
     *   <li>{@code tools[].fullName} — 内部全名（传给 AI 时使用）</li>
     *   <li>{@code tools[].displayName} — 原始工具名（前端展示用，已去除前缀）</li>
     * </ul>
     * </p>
     */
    public Result<List<Map<String, Object>>> getMcpToolsGrouped(String userId) {
        Collection<ToolDefinition> allTools = toolRegistry.getAllTools();
        Set<String> connectedServers = mcpServerManager.getConnectedServers(userId);

        // 按 serverName 分组
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        String mcpPrefix = "mcp_" + userId + "_";

        for (ToolDefinition def : allTools) {
            if (!"MCP".equals(def.getProviderType())) continue;
            String fullName = def.getName();
            if (!fullName.startsWith(mcpPrefix)) continue;

            // 从全名中提取 serverName 和原始工具名
            // 格式: mcp_{userId}_{serverName}_{toolName}
            String inner = fullName.substring(mcpPrefix.length()); // "{serverName}_{toolName}"
            String serverName = null;
            String toolName = null;

            // 从已知的 serverName 列表中匹配以准确分割
            for (String sn : connectedServers) {
                if (inner.startsWith(sn + "_")) {
                    serverName = sn;
                    toolName = inner.substring(sn.length() + 1);
                    break;
                }
            }
            // 也检查已断开但 MySQL 中有记录的 server
            if (serverName == null) {
                QueryWrapper<McpServer> wrapper = new QueryWrapper<>();
                wrapper.eq("user_id", userId);
                List<McpServer> allServers = mcpServerMapper.selectList(wrapper);
                for (McpServer s : allServers) {
                    String prefix = s.getName() + "_";
                    if (inner.startsWith(prefix)) {
                        serverName = s.getName();
                        toolName = inner.substring(prefix.length());
                        break;
                    }
                }
            }

            if (serverName == null) continue; // 无法匹配，跳过

            Map<String, Object> toolInfo = new LinkedHashMap<>();
            toolInfo.put("fullName", fullName);
            toolInfo.put("displayName", toolName);
            toolInfo.put("description", def.getDescription());
            toolInfo.put("parametersSchema", def.getParametersSchema());

            grouped.computeIfAbsent(serverName, k -> new ArrayList<>()).add(toolInfo);
        }

        // 转换为列表格式，包含连接状态
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Map<String, Object> serverGroup = new LinkedHashMap<>();
            serverGroup.put("serverName", entry.getKey());
            serverGroup.put("connected", connectedServers.contains(entry.getKey()));
            serverGroup.put("tools", entry.getValue());
            result.add(serverGroup);
        }

        return Result.success(result);
    }

    /**
     * MCP Server 健康检查。
     * 通过调用远端 listTools 验证连接是否可用。
     */
    public Result<Map<String, Object>> healthCheckMcpServer(String userId, String serverName) {
        boolean ok = mcpServerManager.healthCheck(userId, serverName);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("connected", ok);
        return Result.success(map);
    }

    /**
     * 删除用户所有 MCP Server 配置。
     * 断开所有连接、注销所有 MCP 工具、删除所有 MySQL 记录。
     */
    public Result<Void> removeAllMcpServers(String userId) {
        QueryWrapper<McpServer> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        List<McpServer> servers = mcpServerMapper.selectList(wrapper);

        int count = 0;
        for (McpServer s : servers) {
            mcpServerManager.disconnect(userId, s.getName());
            count++;
        }

        // 批量删除 MySQL 记录
        if (!servers.isEmpty()) {
            mcpServerMapper.delete(wrapper);
        }

        log.info("用户 {} 已删除全部 {} 个 MCP Server", userId, count);
        return Result.success();
    }

    // ==================== Headers JSON 转换工具 ====================

    /** Map → JSON 字符串，用于存入 MySQL。 */
    private String headersToJson(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            log.warn("序列化 headers 失败，将忽略", e);
            return null;
        }
    }

    /** JSON 字符串 → Map，用于从 MySQL 读取。 */
    @SuppressWarnings("unchecked")
    private Map<String, String> jsonToHeaders(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("解析 headers JSON 失败: {}", json, e);
            return Map.of();
        }
    }

    /** 脱敏 headers 值，仅显示首尾各 4 个字符。 */
    private Map<String, String> maskHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return Map.of();
        Map<String, String> masked = new LinkedHashMap<>();
        for (var entry : headers.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.length() > 8) {
                value = value.substring(0, 4) + "***" + value.substring(value.length() - 4);
            }
            masked.put(entry.getKey(), value);
        }
        return masked;
    }
}
