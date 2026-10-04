package me.beiming.aichat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.beiming.aichat.entity.McpServer;
import me.beiming.aichat.mapper.McpServerMapper;
import me.beiming.aichat.toolCalling.McpServerManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动后自动恢复所有已持久化的 MCP Server 连接。
 * <p>
 * 监听 {@link ApplicationReadyEvent}，在所有 Bean 初始化完成后执行，
 * 确保 ToolRegistry 和 McpServerManager 都已就绪。
 * </p>
 */
@Slf4j
@Component
public class McpRestoreConfig {

    @Autowired
    private McpServerMapper mcpServerMapper;

    @Autowired
    private McpServerManager mcpServerManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @EventListener(ApplicationReadyEvent.class)
    public void restoreConnections() {
        List<McpServer> allServers = mcpServerMapper.selectList(null);
        if (allServers.isEmpty()) {
            log.info("没有需要恢复的 MCP Server 连接");
            return;
        }

        log.info("开始恢复 {} 个 MCP Server 连接...", allServers.size());
        int successCount = 0;
        int failCount = 0;

        for (McpServer server : allServers) {
            try {
                Map<String, String> headers = parseHeaders(server.getHeaders());
                mcpServerManager.connect(
                        server.getUserId(), server.getName(), server.getBaseUrl(), headers);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("恢复 MCP Server 连接失败: userId={}, name={}, baseUrl={}",
                        server.getUserId(), server.getName(), server.getBaseUrl(), e);
            }
        }

        log.info("MCP Server 连接恢复完成: 成功 {}/{}, 失败 {}",
                successCount, allServers.size(), failCount);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(headersJson, Map.class);
        } catch (Exception e) {
            log.warn("解析 MCP headers JSON 失败: {}", headersJson, e);
            return Map.of();
        }
    }
}
