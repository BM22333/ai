# MCP 自定义请求头与鉴权方案设计

## 一、问题背景

远程 MCP Server（如联网搜索、数据库代理等）通常需要鉴权：

| 服务 | 鉴权方式 | 示例 |
|------|----------|------|
| Tavily Search | API Key in Header | `X-Tavily-Api-Key: tvly-xxx` |
| Brave Search | API Key in Header | `X-Subscription-Token: BSA-xxx` |
| 自定义 MCP Server | Bearer Token | `Authorization: Bearer eyJ...` |
| 私有部署 | Basic Auth | `Authorization: Basic base64(user:pass)` |

当前系统的 `McpServerManager.connect()` 使用 `HttpClientStreamableHttpTransport.builder(baseUrl).build()`，没有任何自定义请求头的能力，无法连接需要鉴权的远端服务。

---

## 二、SDK 能力调研

### 2.1 关键接口

`HttpClientStreamableHttpTransport.Builder` 提供了以下扩展点（`mcp-core:2.0.0`）：

```java
// 方法签名
public Builder httpRequestCustomizer(McpSyncHttpClientRequestCustomizer);

// McpSyncHttpClientRequestCustomizer 接口
public interface McpSyncHttpClientRequestCustomizer {
    void customize(
        HttpRequest.Builder requestBuilder,  // ← 可在此添加 Header
        String methodName,                    // MCP 方法名，如 "tools/list"
        URI uri,                              // 请求 URI
        String path,                          // 请求路径
        McpTransportContext context           // 传输上下文
    );
}
```

`HttpRequest.Builder` 是 JDK 标准的 `java.net.http.HttpRequest.Builder`，可以直接调用：
- `.header(String name, String value)` — 添加单个请求头
- `.headers(String... headers)` — 批量添加
- `.setHeader(String name, String value)` — 覆盖已有头

### 2.2 组合多个 Customizer

SDK 提供了 `DelegatingMcpSyncHttpClientRequestCustomizer`，可以将多个 customizer 串联执行：

```java
new DelegatingMcpSyncHttpClientRequestCustomizer(List.of(c1, c2, c3));
```

这意味着我们可以：
- 内置一个通用的 `HeaderInjectingCustomizer`（从配置读取 headers 注入）
- 未来用户可以注册自己的 customizer 做更复杂的事（如动态刷新 token）

### 2.3 错误处理

Builder 也提供了鉴权错误处理：

```java
public Builder authorizationErrorHandler(McpHttpClientAuthorizationErrorHandler)
```

当 MCP Server 返回 401/403 时触发回调，可以用于自动刷新 token 等场景。

---

## 三、方案设计

### 3.1 数据模型变更

#### MySQL 表新增字段

```sql
ALTER TABLE mcp_server ADD COLUMN headers TEXT COMMENT '自定义 HTTP 请求头（JSON 格式）';
```

**headers 字段 JSON 格式**（用户可配置）：

```json
[
  { "name": "X-API-Key", "value": "sk-xxxxxxxx" },
  { "name": "Authorization", "value": "Bearer eyJhbGciOi..." }
]
```

或者简化格式（key-value map）：

```json
{
  "X-API-Key": "sk-xxxxxxxx",
  "Authorization": "Bearer eyJhbGciOi..."
}
```

> **推荐使用 JSON 对象格式**（`Map<String, String>`），前端更易编辑且不会重复 key。

#### 决策：使用 `Map<String, String>` 格式

```
{"X-API-Key":"sk-xxx", "Authorization":"Bearer eyJ..."}
```

原因：
1. 前端可以用简单的 key-value 编辑器（两列表格）
2. HTTP 规范中同名字段不允许重复（除 `Set-Cookie` 外）
3. 序列化/反序列化更简单

### 3.2 实体类变更

```java
// McpServer.java
@Data
@TableName("mcp_server")
public class McpServer {
    String userId;
    String name;
    String baseUrl;
    Map<String, String> headers;  // ← 新增：自定义请求头
    List<ToolDefinition> tools;
}
```

### 3.3 DTO 变更

```java
// McpServerDTO.java
@Data
@Schema(description = "MCP Server 配置请求")
public class McpServerDTO {
    @Schema(description = "用户 ID")
    private String userId;

    @Schema(description = "MCP Server 名称（用户自定义）")
    private String name;

    @Schema(description = "MCP Server 的 Streamable HTTP 端点")
    private String baseUrl;

    @Schema(description = "自定义 HTTP 请求头（JSON 对象），如 {\"X-API-Key\":\"sk-xxx\"}",
            example = "{\"X-API-Key\":\"sk-abc123\"}")
    private Map<String, String> headers;  // ← 新增
}
```

### 3.4 核心实现：`HeaderInjectingRequestCustomizer`

```java
package me.beiming.aichat.tool;

import lombok.extern.slf4j.Slf4j;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

/**
 * 从配置注入自定义 Header 到每个 MCP 请求中。
 * 支持 API Key、Bearer Token、自定义鉴权头等场景。
 */
@Slf4j
public class HeaderInjectingRequestCustomizer implements McpSyncHttpClientRequestCustomizer {

    private final Map<String, String> headers;
    private final String serverName; // 仅用于日志

    public HeaderInjectingRequestCustomizer(Map<String, String> headers, String serverName) {
        this.headers = (headers != null) ? Map.copyOf(headers) : Map.of();
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

        // 仅在 debug 级别记录（避免泄露 API Key）
        log.debug("MCP Server [{}] 已注入 {} 个自定义请求头 (method={})",
                serverName, headers.size(), methodName);
    }
}
```

### 3.5 改造 `McpServerManager.connect()`

```java
// 改造前
McpClientTransport transport = HttpClientStreamableHttpTransport
        .builder(baseUrl)
        .build();

// 改造后
Map<String, String> headers = serverConfig.getHeaders();
McpSyncHttpClientRequestCustomizer customizer = null;
if (headers != null && !headers.isEmpty()) {
    customizer = new HeaderInjectingRequestCustomizer(headers, serverName);
}

HttpClientStreamableHttpTransport.Builder transportBuilder =
        HttpClientStreamableHttpTransport.builder(baseUrl);
if (customizer != null) {
    transportBuilder.httpRequestCustomizer(customizer);
}
McpClientTransport transport = transportBuilder.build();
```

---

## 四、安全设计

### 4.1 日志脱敏

`HeaderInjectingRequestCustomizer` 不记录 header 值，仅记录数量：

```java
log.debug("已注入 {} 个自定义请求头", headers.size());  // ✅ 安全
log.debug("Header: {}", headers);                        // ❌ 泄露 API Key
```

`McpServerManager` 中所有日志也要避免打印 headers：

```java
// 连接成功日志
log.info("MCP Server 连接完成: userId={}, serverName={}, tools={}, hasAuth={}",
        userId, serverName, tools.size(), headers != null && !headers.isEmpty());
```

### 4.2 API 响应脱敏

`listMcpServers()` 和 `getMcpToolsGrouped()` 返回的 headers 应对敏感值做遮掩：

```java
// 前端展示用：将 API Key 的前半部分替换为 ***
Map<String, String> maskedHeaders = new LinkedHashMap<>();
for (var entry : headers.entrySet()) {
    String value = entry.getValue();
    if (value.length() > 8) {
        value = value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
    maskedHeaders.put(entry.getKey(), value);
}
```

### 4.3 存储加密（可选，后续迭代）

如果安全要求更高，可在应用层对 headers JSON 做 AES 加密后存储。当前方案：
- headers 以明文 JSON 存储在 MySQL TEXT 列
- 依赖数据库访问控制保护
- 日志不记录 header 值
- API 响应脱敏展示

---

## 五、前端交互设计

### 5.1 添加 MCP Server 表单

```
┌──────────────────────────────────────────────────┐
│  添加 MCP Server                                  │
│                                                  │
│  名称:  [tavily-search                  ]        │
│  地址:  [https://tavily-mcp.example.com/mcp]      │
│                                                  │
│  ┌─ 请求头（可选）──────────────────────────┐    │
│  │                                          │    │
│  │  Key              Value                  │    │
│  │  ┌─────────────┐ ┌─────────────────────┐ │    │
│  │  │ X-API-Key   │ │ sk-abc12345678      │ │    │
│  │  └─────────────┘ └─────────────────────┘ │    │
│  │                                     [✕]  │    │
│  │                                          │    │
│  │  [+ 添加请求头]                          │    │
│  │                                          │    │
│  └──────────────────────────────────────────┘    │
│                                                  │
│  常用预设: [Bearer Token] [API Key] [Basic Auth]  │
│                                                  │
│            [测试连接]  [保存]                     │
└──────────────────────────────────────────────────┘
```

### 5.2 请求体格式

```json
{
  "userId": "uuid-xxx",
  "name": "tavily-search",
  "baseUrl": "https://tavily-mcp.example.com/mcp",
  "headers": {
    "X-Tavily-Api-Key": "tvly-xxxxxxxxxxxxx"
  }
}
```

### 5.3 列表展示时脱敏

```
┌─ tavily-search ────────────── 🟢 已连接 ─────┐
│  https://tavily-mcp.example.com/mcp           │
│  鉴权: X-Tavily-Api-Key: tvly-***xxxx         │
│  可用工具: search, extract                    │
│  [断开] [重连]                                │
└──────────────────────────────────────────────┘
```

---

## 六、典型使用场景

### 场景 1：API Key 鉴权（Tavily Search）

```json
{
  "name": "tavily",
  "baseUrl": "https://mcp.tavily.com/mcp",
  "headers": {
    "X-Tavily-Api-Key": "tvly-xxxxxxxxxxxxx"
  }
}
```

### 场景 2：Bearer Token 鉴权

```json
{
  "name": "my-agent",
  "baseUrl": "https://agent.example.com/mcp",
  "headers": {
    "Authorization": "Bearer eyJhbGciOiJIUzI1NiIs..."
  }
}
```

### 场景 3：多 Header 组合

```json
{
  "name": "enterprise-mcp",
  "baseUrl": "https://mcp.enterprise.com/api",
  "headers": {
    "X-API-Key": "ent-key-123",
    "X-Tenant-ID": "tenant-456",
    "X-Client-Version": "1.0"
  }
}
```

---

## 七、实施步骤

| 步骤 | 内容 | 影响文件 |
|------|------|----------|
| 1 | `mcp_server` 表新增 `headers` TEXT 列 | SQL 迁移脚本 |
| 2 | `McpServer` 实体新增 `headers` 字段（`Map<String,String>`） | `entity/McpServer.java` |
| 3 | `McpServerDTO` 新增 `headers` 字段 | `dto/McpServerDTO.java` |
| 4 | 新建 `HeaderInjectingRequestCustomizer` | `tool/HeaderInjectingRequestCustomizer.java` |
| 5 | `McpServerManager.connect()` 注入 customizer | `tool/McpServerManager.java` |
| 6 | `McpService.listMcpServers()` 添加脱敏逻辑 | `service/McpService.java` |
| 7 | `McpBootstrap.restoreConnections()` 读取 headers | `config/McpBootstrap.java` |
| 8 | 更新前端适配文档 | `docs/前端MCP集成适配指南.md` |

---

## 八、未来扩展

### 8.1 动态 Token 刷新

利用 SDK 的 `authorizationErrorHandler` 回调：

```java
transportBuilder.authorizationErrorHandler((statusCode, responseBody) -> {
    if (statusCode == 401) {
        // 触发 token 刷新逻辑
        String newToken = refreshToken();
        // 更新存储的 headers
        updateHeaders(userId, serverName, "Authorization", "Bearer " + newToken);
    }
});
```

### 8.2 插件式 Customizer

用户可以注册自定义的 `McpSyncHttpClientRequestCustomizer` 实现类，实现比静态 headers 更复杂的逻辑（如根据请求方法动态选择 API Key、请求签名等）。
