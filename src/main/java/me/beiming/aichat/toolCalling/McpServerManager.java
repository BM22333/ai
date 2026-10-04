package me.beiming.aichat.toolCalling;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import me.beiming.aichat.entity.McpServer;
import me.beiming.aichat.entity.ToolDefinition;
import me.beiming.aichat.mapper.McpServerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Server 连接管理器。
 * <p>
 * 管理所有 MCP 客户端的生命周期：连接、断开、重连、健康检查、工具发现与执行。
 * 使用 Streamable HTTP 传输（MCP v2.0.0 默认），支持多用户隔离。
 * </p>
 */
@Slf4j
@Service
public class McpServerManager {

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private McpServerMapper mcpServerMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 客户端最大空闲时间（秒）。超过此时间未使用的连接将被回收。
     */
    private static final long CLIENT_MAX_IDLE_SECONDS = 1800; // 30 分钟

    /**
     * 客户端最大存活时间（秒）。超过此时间的连接将被强制回收并重建。
     */
    private static final long CLIENT_MAX_LIFETIME_SECONDS = 21600; // 6 小时

    /**
     * 定时淘汰任务的执行间隔（毫秒）。
     */
    private static final long EVICTION_INTERVAL_MS = 300_000; // 5 分钟

    /**
     * 在线客户端缓存。
     * Key:   "{userId}_{serverName}"
     * Value: ClientEntry（含 McpSyncClient + 存活时间戳）
     */
    private final Map<String, ClientEntry> clients = new ConcurrentHashMap<>();

    /**
     * 用户+Server → 工具名集合 的映射。
     * Key:   "mcp_{userId}_{serverName}"
     * Value: 该 Server 下所有原始工具名的集合
     */
    private final Map<String, Set<String>> toolMetaMap = new ConcurrentHashMap<>();

    /**
     * 连接到 MCP Server，发现工具并注册到 ToolRegistry。
     *
     * @param userId     用户 ID
     * @param serverName MCP Server 名称（用户自定义，如 "weather"）
     * @param baseUrl    MCP Server 的 Streamable HTTP 端点
     * @param headers    自定义 HTTP 请求头（可为 null 或空 Map）
     * @return 发现的工具定义列表
     * @throws McpConnectionException 连接失败时抛出
     */
    public List<ToolDefinition> connect(String userId, String serverName, String baseUrl,
                                         Map<String, String> headers) {
        String clientKey = buildClientKey(userId, serverName);

        // 1. 如果已存在连接，先断开
        disconnect(userId, serverName);

        McpSyncClient client = null;
        try {
            // 2. 构建 Streamable HTTP 传输层（注入自定义请求头）
            var transportBuilder = HttpClientStreamableHttpTransport.builder(baseUrl)
                    .endpoint(baseUrl);  // 强制使用完整路径，防止 SDK 截断中间路径
            if (headers != null && !headers.isEmpty()) {
                transportBuilder.httpRequestCustomizer(
                        new HeaderInjectingRequestCustomizer(headers, serverName));
            }
            McpClientTransport transport = transportBuilder.build();

            // 3. 创建同步客户端
            client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(30))
                    .build();

            // 4. 初始化（握手 + 协议版本协商）
            if (headers != null && !headers.isEmpty()) {
                log.info("MCP 正在连接（含鉴权）: serverName={}, headerKeys={}",
                        serverName, headers.keySet());
            }
            try {
                client.initialize();
            } catch (Exception initEx) {
                String detail = initEx.getMessage();
                // 尝试提取更底层的 HTTP 错误信息
                Throwable cause = initEx.getCause();
                while (cause != null) {
                    String causeMsg = cause.getMessage();
                    if (causeMsg != null && !causeMsg.isEmpty()) {
                        detail = detail + " | 原因: " + causeMsg;
                    }
                    cause = cause.getCause();
                }
                throw new McpConnectionException(
                        "MCP Server 鉴权失败或不可达: \"" + serverName + "\" — " + detail, initEx);
            }
            log.info("MCP 客户端初始化成功: userId={}, serverName={}, baseUrl={}, hasAuth={}",
                    userId, serverName, baseUrl, headers != null && !headers.isEmpty());

            // 5. 发现工具
            McpSchema.ListToolsResult toolsResult = client.listTools();
            List<McpSchema.Tool> remoteTools = toolsResult.tools();
            log.info("发现 {} 个远程工具: userId={}, serverName={}",
                    remoteTools.size(), userId, serverName);

            // 6. 转换并注册到 ToolRegistry
            List<ToolDefinition> registeredTools = new ArrayList<>();
            String metaKey = buildMetaKey(userId, serverName);
            Set<String> originalNames = ConcurrentHashMap.newKeySet();

            for (McpSchema.Tool remoteTool : remoteTools) {
                String registeredName = buildToolName(userId, serverName, remoteTool.name());

                ToolDefinition def = new ToolDefinition();
                def.setName(registeredName);
                def.setDescription(remoteTool.description() != null
                        ? remoteTool.description() : remoteTool.name());
                def.setProviderType("MCP");

                // MCP 的 inputSchema 直接是 Map 格式，转为内部参数格式
                def.setParametersSchema(normalizeSchema(remoteTool.inputSchema()));

                // MCP 工具没有 Bean/Method，留空
                def.setBean(null);
                def.setMethod(null);

                // 注册到 ToolRegistry
                toolRegistry.registerMcpTool(def);

                // 记录原始工具名
                originalNames.add(remoteTool.name());

                registeredTools.add(def);
                log.info("注册 MCP 工具: {} → {}", registeredName, remoteTool.name());
            }

            // 将工具名集合放入路由映射
            toolMetaMap.put(metaKey, originalNames);

            // 7. 缓存客户端（带存活时间戳）
            clients.put(clientKey, new ClientEntry(client, System.currentTimeMillis()));
            log.info("MCP Server 连接完成: userId={}, serverName={}, tools={}",
                    userId, serverName, registeredTools.size());

            return registeredTools;

        } catch (McpConnectionException e) {
            throw e;
        } catch (Exception e) {
            log.error("MCP Server 连接失败: userId={}, serverName={}, baseUrl={}",
                    userId, serverName, baseUrl, e);
            // 清理失败的连接痕迹
            if (client != null) {
                try { client.close(); } catch (Exception ignored) {}
            }
            disconnect(userId, serverName);
            throw new McpConnectionException(
                    "无法连接到 MCP Server \"" + serverName + "\": " + e.getMessage(), e);
        }
    }

    /**
     * 断开 MCP Server 连接，注销其所有工具。
     *
     * @param userId     用户 ID
     * @param serverName MCP Server 名称
     */
    public void disconnect(String userId, String serverName) {
        String clientKey = buildClientKey(userId, serverName);
        String prefix = buildToolPrefix(userId, serverName);

        // 1. 注销所有工具
        toolRegistry.unregisterMcpToolsByPrefix(prefix);

        // 2. 清理元数据
        toolMetaMap.remove(buildMetaKey(userId, serverName));

        // 3. 关闭客户端
        ClientEntry entry = clients.remove(clientKey);
        if (entry != null) {
            try {
                entry.client.close();
                log.info("MCP 客户端已关闭: userId={}, serverName={}, 存活={}s",
                        userId, serverName, entry.ageSeconds());
            } catch (Exception e) {
                log.warn("关闭 MCP 客户端时出错: userId={}, serverName={}", userId, serverName, e);
            }
        }
    }

    /**
     * 执行 MCP 工具。
     *
     * @param registeredName 注册到 ToolRegistry 的完整名称（如 "mcp_userId_weather_get_forecast"）
     * @param argumentsJson  JSON 参数字符串
     * @return 执行结果字符串
     */
    public String execute(String registeredName, String argumentsJson) {
        // 注册名格式: "mcp_{userId}_{serverName}_{toolName}"
        // 从 toolMetaMap 的 key 中匹配前缀，找到对应的 userId+serverName
        String userId = null;
        String serverName = null;
        String originalName = null;

        // 按 metaKey 长度降序排列，防止前缀重叠导致路由错误
        // 例如: "mcp_user1_test2" 必须在 "mcp_user1_test" 之前匹配
        // 否则 "mcp_user1_test2_search" 会错误匹配到 "mcp_user1_test_"
        List<Map.Entry<String, Set<String>>> sortedEntries = new ArrayList<>(toolMetaMap.entrySet());
        sortedEntries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        for (var entry : sortedEntries) {
            String metaKey = entry.getKey(); // "mcp_{userId}_{serverName}"
            if (registeredName.startsWith(metaKey + "_")) {
                originalName = registeredName.substring(metaKey.length() + 1);
                // 从 metaKey 解析 userId 和 serverName: "mcp_{userId}_{serverName}"
                String inner = metaKey.substring("mcp_".length()); // "{userId}_{serverName}"
                int split = inner.indexOf('_');
                if (split > 0) {
                    userId = inner.substring(0, split);
                    serverName = inner.substring(split + 1);
                }
                break;
            }
        }

        if (userId == null || serverName == null || originalName == null) {
            return "错误: 工具 " + registeredName + " 的 MCP 路由信息不存在";
        }

        // 获取或自动重连客户端（缓存失效时自动重建）
        ClientEntry entry = getOrReconnectClient(userId, serverName);
        if (entry == null) {
            return "错误: MCP Server \"" + serverName + "\" 未连接且自动重连失败，请检查配置或重新添加";
        }

        // 更新最后使用时间
        entry.lastUsedAt = System.currentTimeMillis();

        try {
            // 解析参数
            Map<String, Object> args;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(argumentsJson, Map.class);
                args = parsed;
            } catch (Exception e) {
                args = Collections.emptyMap();
            }

            // 直接将 Map 参数传给 CallToolRequest（SDK 原生接受 Map）
            McpSchema.CallToolResult result = entry.client.callTool(
                    new McpSchema.CallToolRequest(originalName, args));

            // 提取文本内容
            if (result.content() != null && !result.content().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (McpSchema.Content content : result.content()) {
                    if ("text".equals(content.type())) {
                        if (content instanceof McpSchema.TextContent textContent) {
                            sb.append(textContent.text());
                        }
                    }
                }
                return !sb.isEmpty() ? sb.toString() : "工具执行完成（无文本输出）";
            }

            return "工具执行完成（无输出）";

        } catch (Exception e) {
            log.error("MCP 工具执行失败: tool={}, serverName={}",
                    originalName, serverName, e);
            return "MCP 工具执行错误: " + e.getMessage();
        }
    }

    /**
     * 健康检查：通过调用 listTools 验证 MCP Server 是否可达。
     */
    public boolean healthCheck(String userId, String serverName) {
        String clientKey = buildClientKey(userId, serverName);
        ClientEntry entry = clients.get(clientKey);
        if (entry == null) return false;
        try {
            entry.client.listTools();
            entry.lastUsedAt = System.currentTimeMillis();
            return true;
        } catch (Exception e) {
            log.warn("MCP 健康检查失败: userId={}, serverName={}", userId, serverName);
            return false;
        }
    }

    /**
     * 获取某用户所有已连接的 MCP Server 名称。
     */
    public Set<String> getConnectedServers(String userId) {
        String prefix = userId + "_";
        Set<String> servers = new LinkedHashSet<>();
        for (String key : clients.keySet()) {
            if (key.startsWith(prefix)) {
                servers.add(key.substring(prefix.length()));
            }
        }
        return servers;
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭所有 MCP 客户端连接 (共 {} 个)...", clients.size());
        for (Map.Entry<String, ClientEntry> entry : clients.entrySet()) {
            try {
                entry.getValue().client.close();
                log.info("MCP 客户端已关闭: key={}", entry.getKey());
            } catch (Exception e) {
                log.warn("关闭 MCP 客户端时出错: key={}", entry.getKey(), e);
            }
        }
        clients.clear();
        toolMetaMap.clear();
        log.info("所有 MCP 客户端已关闭");
    }

    /**
     * 定时淘汰过期或空闲过久的客户端连接。
     * 每 {@value #EVICTION_INTERVAL_MS}ms 执行一次。
     */
    @Scheduled(fixedDelay = EVICTION_INTERVAL_MS)
    public void evictStaleClients() {
        if (clients.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<String> toEvict = new ArrayList<>();

        for (var entry : clients.entrySet()) {
            ClientEntry ce = entry.getValue();
            if (isExpired(now, ce)) {
                toEvict.add(entry.getKey());
            }
        }

        for (String key : toEvict) {
            ClientEntry ce = clients.get(key);
            String reason = ce.ageSeconds() >= CLIENT_MAX_LIFETIME_SECONDS ? "超过最大存活时间" : "空闲过久";
            log.info("淘汰过期 MCP 客户端: key={}, age={}s, reason={}", key, ce.ageSeconds(), reason);
            // 从 key 解析 userId 和 serverName，仅关闭连接，保留工具注册和路由元数据
            int split = key.indexOf('_');
            if (split > 0) {
                String userId = key.substring(0, split);
                String serverName = key.substring(split + 1);
                closeClientOnly(userId, serverName);
            }
        }

        if (!toEvict.isEmpty()) {
            log.info("本轮淘汰 {} 个 MCP 客户端, 剩余 {} 个", toEvict.size(), clients.size());
        }
    }

    /**
     * 仅关闭客户端连接，不注销工具、不清除路由元数据。
     * 用于定时淘汰场景，保留工具的"影子"以便后续自动重连。
     */
    private void closeClientOnly(String userId, String serverName) {
        String clientKey = buildClientKey(userId, serverName);
        ClientEntry entry = clients.remove(clientKey);
        if (entry != null) {
            try {
                entry.client.close();
                log.info("MCP 客户端连接已关闭（保留工具注册）: userId={}, serverName={}, 存活={}s",
                        userId, serverName, entry.ageSeconds());
            } catch (Exception e) {
                log.warn("关闭 MCP 客户端时出错: userId={}, serverName={}", userId, serverName, e);
            }
        }
    }

    /**
     * 获取客户端，缓存失效时自动从 MySQL 读取配置并重建连接。
     *
     * @return 可用的 ClientEntry，若重连失败则返回 null
     */
    private ClientEntry getOrReconnectClient(String userId, String serverName) {
        String clientKey = buildClientKey(userId, serverName);
        ClientEntry entry = clients.get(clientKey);

        // 客户端存在且未过期，直接返回
        if (entry != null && !isExpired(entry)) {
            return entry;
        }

        // 过期 → 清理旧连接（保留工具注册和路由元数据）
        if (entry != null) {
            log.info("MCP 客户端已过期，自动重连: userId={}, serverName={}, age={}s",
                    userId, serverName, entry.ageSeconds());
            closeClientOnly(userId, serverName);
        } else {
            log.info("MCP 客户端不存在，尝试自动重连: userId={}, serverName={}", userId, serverName);
        }

        // 从 MySQL 读取配置
        QueryWrapper<McpServer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("name", serverName);
        McpServer server = mcpServerMapper.selectOne(queryWrapper);

        if (server == null) {
            log.warn("自动重连失败，MySQL 中无此配置: userId={}, serverName={}", userId, serverName);
            return null;
        }

        // 重建连接（connect 内部会先 disconnect 清理残留，再注册工具）
        try {
            Map<String, String> headers = parseHeaders(server.getHeaders());
            connect(userId, serverName, server.getBaseUrl(), headers);
            log.info("自动重连成功: userId={}, serverName={}", userId, serverName);
            return clients.get(clientKey);
        } catch (McpConnectionException e) {
            log.error("自动重连失败: userId={}, serverName={}, baseUrl={}",
                    userId, serverName, server.getBaseUrl(), e);
            return null;
        }
    }

    /**
     * 判断客户端是否过期（超过最大存活时间 或 空闲超过最大空闲时间）。
     */
    private boolean isExpired(ClientEntry entry) {
        return isExpired(System.currentTimeMillis(), entry);
    }

    private boolean isExpired(long now, ClientEntry entry) {
        long age = (now - entry.createdAt) / 1000;
        long idle = (now - entry.lastUsedAt) / 1000;
        return age >= CLIENT_MAX_LIFETIME_SECONDS || idle >= CLIENT_MAX_IDLE_SECONDS;
    }

    /** 将 headers JSON 字符串解析为不可变的 Map。 */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> map = objectMapper.readValue(headersJson, Map.class);
            return map != null ? Map.copyOf(map) : Map.of();
        } catch (Exception e) {
            log.warn("解析 MCP headers JSON 失败，将忽略自定义请求头: {}", headersJson, e);
            return Map.of();
        }
    }

    /** 构建客户端缓存 Key: "{userId}_{serverName}" */
    private String buildClientKey(String userId, String serverName) {
        return userId + "_" + serverName;
    }

    /** 构建元数据 Map 的 Key: "mcp_{userId}_{serverName}" */
    private String buildMetaKey(String userId, String serverName) {
        return "mcp_" + userId + "_" + serverName;
    }

    /** 构建注册工具名称: "mcp_{userId}_{serverName}_{originalName}" */
    private String buildToolName(String userId, String serverName, String originalName) {
        return "mcp_" + userId + "_" + serverName + "_" + originalName;
    }

    /** 构建工具名前缀: "mcp_{userId}_{serverName}_" */
    private String buildToolPrefix(String userId, String serverName) {
        return "mcp_" + userId + "_" + serverName + "_";
    }

    /**
     * 规范化 MCP 工具的 inputSchema，确保包含 type 和 properties 字段。
     * MCP SDK v2.0.0 的 inputSchema() 返回 {@code Map<String, Object>}。
     */
    private Map<String, Object> normalizeSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("type", "object");
            empty.put("properties", Collections.emptyMap());
            return empty;
        }
        // 确保 type 字段存在
        if (!schema.containsKey("type")) {
            schema.put("type", "object");
        }
        // 确保 properties 字段存在
        if (!schema.containsKey("properties")) {
            schema.put("properties", Collections.emptyMap());
        }
        return schema;
    }

    /**
     * 客户端缓存条目，包含连接和存活时间戳。
     */
    private static class ClientEntry {
        final McpSyncClient client;
        final long createdAt;
        volatile long lastUsedAt;

        ClientEntry(McpSyncClient client, long now) {
            this.client = client;
            this.createdAt = now;
            this.lastUsedAt = now;
        }

        long ageSeconds() {
            return (System.currentTimeMillis() - createdAt) / 1000;
        }
    }

    /**
     * MCP 连接异常。
     */
    public static class McpConnectionException extends RuntimeException {
        public McpConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
