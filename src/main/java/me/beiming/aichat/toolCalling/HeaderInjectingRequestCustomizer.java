package me.beiming.aichat.toolCalling;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

/**
 * 从配置注入自定义 HTTP 请求头到每个 MCP 请求中。
 * <p>
 * 实现 {@link McpSyncHttpClientRequestCustomizer} 接口，
 * 在每次 MCP 请求发出前向 {@link HttpRequest.Builder} 中注入用户配置的 Header。
 * 支持 API Key、Bearer Token、自定义鉴权头等场景。
 * </p>
 *
 * <p>线程安全：headers 在构造时通过 {@link Map#copyOf} 复制，后续不可变。</p>
 */
@Slf4j
public class HeaderInjectingRequestCustomizer implements McpSyncHttpClientRequestCustomizer {

    private final Map<String, String> headers;
    private final String serverName;

    /**
     * @param headers    用户配置的 Header 键值对（不可变副本）
     * @param serverName MCP Server 名称（仅用于日志标识）
     */
    public HeaderInjectingRequestCustomizer(Map<String, String> headers, String serverName) {
        this.headers = (headers != null && !headers.isEmpty())
                ? Map.copyOf(headers) : Map.of();
        this.serverName = serverName;
    }

    @Override
    public void customize(
            HttpRequest.Builder requestBuilder,
            String methodName,
            URI uri,
            String path,
            McpTransportContext context) {

        if (headers.isEmpty()) return;

        for (var entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
        }

        // 记录实际请求的 URL 和 Header keys（不记录 value，防止泄露 API Key）
        log.info("MCP [{}] → {} {} | 注入 {} 个请求头 ({})",
                serverName, methodName, uri, headers.size(), headers.keySet());
    }
}
