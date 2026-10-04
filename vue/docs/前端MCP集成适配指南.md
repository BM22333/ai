@# 前端 MCP 集成适配指南

## 概述

MCP（Model Context Protocol）允许用户接入远程工具服务器（如天气查询、数据库操作等）。后端已完成 MCP 官方 SDK 集成，前端需要适配以下要点：

1. **隐藏内部命名前缀** — 用户看到的是"天气查询"，不是 `mcp_uuid_weather_get_forecast`
2. **MCP 工具不自动暴露** — 需要用户在会话中显式选择启用
3. **按 Server 分组展示** — 工具应归属到对应的 MCP Server 下

---

## 一、内部命名约定（前端需要知道但不应展示）

### 1.1 命名格式

```
mcp_{userId}_{serverName}_{toolName}
  │       │          │           │
  │       │          │           └── 远端工具原始名称，如 "get_forecast"
  │       │          └── 用户自定义的 Server 名称，如 "weather"
  │       └── 当前用户 ID（UUID）
  └── 固定前缀，标识这是一个 MCP 远程工具
```

### 1.2 解析方法

```typescript
interface McpToolInfo {
  userId: string;
  serverName: string;   // 用户给 Server 起的名字
  toolName: string;     // 远端工具原始名称
  displayName: string;  // 展示给用户的名称 = serverName + toolName
}

function parseMcpToolName(fullName: string): McpToolInfo | null {
  if (!fullName.startsWith('mcp_')) return null;

  const inner = fullName.slice(4); // 去掉 "mcp_"
  const parts = inner.split('_');

  // userId 是第一部分（UUID 不含下划线，可以安全分割）
  const userId = parts[0];
  // 剩余部分需要结合 serverName 来确定分界点
  // 建议从 API 获取 serverName 列表后精确匹配
  const remaining = parts.slice(1).join('_');

  return {
    userId,
    serverName: '',      // 需要结合 knownServers 二次解析
    toolName: remaining,  // 暂存
    displayName: remaining,
  };
}
```

> **关键约束**：`userId` 是 UUID（如 `a1b2c3d4-...`），不含下划线，所以 `mcp_` 后的第一个 `_` 前一定是 userId。但 `serverName` 和 `toolName` 都可能含下划线，需要结合已知的 serverName 列表才能精确分割。

### 1.3 推荐方案：后端返回分组数据

前端不应自行解析工具名，而是通过 API 获取已分组的数据（见下文）。

---

## 二、API 接口说明

### 2.1 MCP Server 管理

#### 新增 Server

```
POST /mcp/servers
Content-Type: application/json

{
  "userId": "uuid-xxx",
  "name": "weather",                              // 用户自定义名称
  "baseUrl": "https://weather-mcp.example.com/mcp" // MCP Server 端点
}

// 成功响应 — 返回该 Server 下所有工具的定义
{
  "code": 1,
  "data": [
    {
      "name": "mcp_uuid_weather_get_forecast",    // 内部全名
      "description": "获取指定城市的天气预报",
      "providerType": "MCP",
      "parametersSchema": {
        "type": "object",
        "properties": {
          "city": { "type": "string", "description": "城市名称" }
        },
        "required": ["city"]
      }
    }
  ]
}
```

#### 删除 Server

```
DELETE /mcp/servers/weather
Content-Type: application/json

{ "userId": "uuid-xxx" }

// 成功: { "code": 1 }
// 删除后工具自动注销，AI 不可再调用
```

#### 列出所有 Server（含连接状态）

```
POST /mcp/servers/list
Content-Type: application/json

{ "userId": "uuid-xxx" }

// 响应
{
  "code": 1,
  "data": [
    {
      "name": "weather",
      "baseUrl": "https://weather-mcp.example.com/mcp",
      "connected": true       // false = 连接已过期/断开
    }
  ]
}
```

#### 重连 Server

```
POST /mcp/servers/weather/reconnect
Content-Type: application/json

{ "userId": "uuid-xxx" }

// 成功响应同"新增 Server"
```

### 2.2 获取 MCP 工具列表（按 Server 分组）⭐ 推荐

```
POST /mcp/tools
Content-Type: application/json

{ "userId": "uuid-xxx" }

// 响应 — 已按 Server 分组，displayName 可直接展示
{
  "code": 1,
  "data": [
    {
      "serverName": "weather",
      "connected": true,
      "tools": [
        {
          "fullName": "mcp_uuid_weather_get_forecast",
          "displayName": "get_forecast",
          "description": "获取指定城市的天气预报",
          "parametersSchema": {
            "type": "object",
            "properties": {
              "city": { "type": "string", "description": "城市名称" }
            },
            "required": ["city"]
          }
        }
      ]
    }
  ]
}
```

> **这是前端工具选择界面的首选数据源**。`displayName` 已去除前缀，`fullName` 用于传给 AI。

### 2.3 健康检查

```
POST /mcp/servers/weather/health
Content-Type: application/json

{ "userId": "uuid-xxx" }

// 响应
{ "code": 1, "data": { "connected": true } }
```

### 2.4 删除所有 MCP Server

```
DELETE /mcp/servers
Content-Type: application/json

{ "userId": "uuid-xxx" }

// 成功: { "code": 1 }
// 断开所有连接、注销所有 MCP 工具、删除所有 MySQL 记录
```

### 2.5 注销用户

```
POST /user/deactivate
Content-Type: application/json

{ "userId": "uuid-xxx" }

// 成功: { "code": 1 }
// 执行以下清理：
//   1. 用户状态设为禁用
//   2. 清除所有登录令牌（强制重新登录）
//   3. 删除所有 API 配置
//   4. 断开所有 MCP Server 并删除配置
// 注销后数据不可恢复
```

> **前端行为**：注销成功后应清除本地 token，跳转到登录页。

### 2.6 工具列表（全部，区分 BUILTIN / MCP）

```
POST /ai/tools

// 响应
[
  {
    "name": "calculator",                // BUILTIN 工具：名称简洁
    "description": "数学表达式计算",
    "providerType": "BUILTIN"
  },
  {
    "name": "mcp_uuid_weather_get_forecast",  // MCP 工具：带有完整前缀
    "description": "获取指定城市的天气预报",
    "providerType": "MCP"
  }
]
```

> **重点**：MCP 工具虽然在 `/ai/tools` 中列出，但**不会自动暴露给 AI**。用户必须在会话配置或请求中显式选择。建议优先使用 `/mcp/tools` 接口，已自动分组且提供 displayName。

---

## 三、前端展示策略：隐藏前缀

### 3.1 使用 `/mcp/tools` 获取分组数据（推荐）

```typescript
interface McpToolInfo {
  fullName: string;       // "mcp_uuid_weather_get_forecast"
  displayName: string;    // "get_forecast" — 已去除前缀，直接展示
  description: string;
  parametersSchema?: Record<string, any>;
}

interface McpServerGroup {
  serverName: string;
  connected: boolean;
  tools: McpToolInfo[];
}

interface DisplayTool {
  fullName: string;
  displayName: string;
  description: string;
  providerType: 'BUILTIN' | 'MCP';
  serverName?: string;
  parametersSchema?: Record<string, any>;
}

/**
 * 获取前端展示用的工具列表。
 * 使用 /mcp/tools 接口，无需手动解析前缀。
 */
async function fetchToolsForDisplay(
  userId: string
): Promise<{ builtin: DisplayTool[]; mcpGroups: McpServerGroup[] }> {
  // 并行获取 BUILTIN 工具和 MCP 工具
  const [builtinRes, mcpRes] = await Promise.all([
    fetch('/ai/tools', { method: 'POST' }),
    fetch('/mcp/tools', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId }),
    }),
  ]);

  const builtinTools: DisplayTool[] = (await builtinRes.json())
    .filter((t: any) => t.providerType === 'BUILTIN')
    .map((t: any) => ({
      fullName: t.name,
      displayName: t.name,
      description: t.description,
      providerType: 'BUILTIN' as const,
    }));

  const mcpData = await mcpRes.json();
  const mcpGroups: McpServerGroup[] = mcpData.data || [];

  return { builtin: builtinTools, mcpGroups };
}
```

### 3.2 工具选择 UI 示意

```
┌─────────────────────────────────────────┐
│  会话设置 — 工具选择                      │
├─────────────────────────────────────────┤
│                                          │
│  📦 内置工具                              │
│  ┌─────────────────────────────────────┐ │
│  │ ☑ calculator    数学表达式计算       │ │
│  │ ☐ date_time     日期时间查询        │ │
│  └─────────────────────────────────────┘ │
│                                          │
│  🔌 MCP Server: weather  🟢已连接        │
│  ┌─────────────────────────────────────┐ │
│  │ ☐ get_forecast  获取天气预报        │ │
│  │ ☐ get_alert     获取天气预警        │ │
│  └─────────────────────────────────────┘ │
│                                          │
│  🔌 MCP Server: database  🔴已断开       │
│  ┌─────────────────────────────────────┐ │
│  │ ⚠️ 连接已断开，点击重连              │ │
│  └─────────────────────────────────────┘ │
│                                          │
│  [+ 添加 MCP Server]                     │
│                                          │
│         [保存]  [取消]                    │
└─────────────────────────────────────────┘
```

### 3.3 对话中工具调用卡片的展示

当 AI 调用 MCP 工具时，SSE 流中出现的工具名是全名 `mcp_uuid_weather_get_forecast`。前端应展示为：

```
┌ 🔧 工具调用 ────────────────────────┐
│ 🌐 weather / get_forecast          │  ← 隐藏 mcp_uuid_ 前缀
│ ──────────────────────────────────  │
│ 参数: {"city": "杭州"}              │
│ ✅ 结果: "杭州明天晴，25°C"          │
└────────────────────────────────────┘
```

```typescript
function getDisplayToolName(fullName: string, knownServers: ServerInfo[]): string {
  const parsed = matchMcpServer(fullName, knownServers);
  if (parsed) {
    return `${parsed.serverName} / ${parsed.toolName}`;
  }
  return fullName; // fallback: 非 MCP 工具直接展示原名
}
```

---

## 四、核心规则：MCP 工具不自动暴露

### 4.1 三种启用模式的行为

| 前端操作 | `enableTools` | 传给 AI 的工具 |
|----------|---------------|---------------|
| 关闭工具开关 | `false` | 空 |
| 开启全部工具 | `true` | **仅 BUILTIN**，MCP 不暴露 |
| 手动选择工具 | `null`（或不传） | `toolPreferences`（会话级）∪ `toolNames`（请求级） |

### 4.2 发送请求时的参数

```typescript
// ❌ 错误：enableTools=true 不会暴露 MCP 工具
await fetch('/ai/chat', {
  body: JSON.stringify({
    conversationId: 'xxx',
    message: '查一下北京天气',
    enableTools: true,  // MCP 工具仍不会被发送
  }),
});

// ✅ 正确：在 toolNames 中显式指定
await fetch('/ai/chat', {
  body: JSON.stringify({
    conversationId: 'xxx',
    message: '查一下北京天气',
    // enableTools 不传或为 null（合并模式）
    toolNames: [
      'mcp_uuid_weather_get_forecast',  // 显式指定
    ],
  }),
});

// ✅ 也可以保存到会话级别的 toolPreferences
await fetch('/conversation/update', {
  body: JSON.stringify({
    conversationId: 'xxx',
    toolPreferences: JSON.stringify([
      'mcp_uuid_weather_get_forecast',
    ]),
  }),
});
```

### 4.3 前端推荐流程

```
用户进入会话设置
  │
  ├── 展示 BUILTIN 工具（直接勾选）
  │
  ├── 展示各 MCP Server 下的工具（折叠面板）
  │     └── 用户勾选 → 将全名加入 toolPreferences
  │
  └── 保存会话配置
        │
        ▼
   下次对话 → 勾选的工具自动生效
```

---

## 五、MCP Server 管理界面建议

### 5.1 完整页面布局

```
┌──────────────────────────────────────────────────────┐
│  MCP Server 管理                                      │
│                                                       │
│  ┌──────────────────────────────────────────────┐    │
│  │ 添加 MCP Server                          [+] │    │
│  └──────────────────────────────────────────────┘    │
│                                                       │
│  ┌─ weather ───────────────────── 🟢 已连接 ─────┐   │
│  │  https://weather-mcp.example.com/mcp           │   │
│  │  可用工具: get_forecast, get_alert             │   │
│  │  [断开] [重连]                                 │   │
│  └────────────────────────────────────────────────┘   │
│                                                       │
│  ┌─ database ──────────────────── 🔴 已断开 ─────┐   │
│  │  https://db-mcp.example.com/mcp                │   │
│  │  可用工具: query, insert                       │   │
│  │  [删除] [重连]                                 │   │
│  └────────────────────────────────────────────────┘   │
│                                                       │
└──────────────────────────────────────────────────────┘
```

### 5.2 添加 MCP Server 表单

```tsx
function AddMcpServerForm({ userId, onAdded }: Props) {
  const [name, setName] = useState('');
  const [baseUrl, setBaseUrl] = useState('');
  const [status, setStatus] = useState<'idle' | 'connecting' | 'error'>('idle');

  async function handleSubmit() {
    setStatus('connecting');
    try {
      const res = await fetch('/mcp/servers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, name, baseUrl }),
      });
      const data = await res.json();

      if (data.code === 1) {
        // data.data 是该 Server 下的工具列表
        onAdded({ name, baseUrl, tools: data.data });
        setName('');
        setBaseUrl('');
        setStatus('idle');
      } else {
        setStatus('error');
        // data.msg 包含错误详情（连接失败等）
        alert(data.msg);
      }
    } catch {
      setStatus('error');
    }
  }

  return (
    <div className="add-mcp-form">
      <input
        placeholder="Server 名称（如 weather）"
        value={name}
        onChange={e => setName(e.target.value)}
      />
      <input
        placeholder="MCP 端点 URL"
        value={baseUrl}
        onChange={e => setBaseUrl(e.target.value)}
      />
      <button
        onClick={handleSubmit}
        disabled={!name || !baseUrl || status === 'connecting'}
      >
        {status === 'connecting' ? '连接中...' : '添加'}
      </button>
    </div>
  );
}
```

---

## 六、缓存与重连

### 6.1 用户无感知的自动重连

后端在工具调用时会自动重连过期的 MCP 连接，前端无需特殊处理。但以下场景需要注意：

| 场景 | 前端行为 |
|------|----------|
| Server 列表显示 `connected: false` | 展示"已断开"标签，提供 [重连] 按钮 |
| AI 调用工具返回错误（含"自动重连失败"） | 提示用户去 MCP 管理页重连 |
| 刚添加成功的 Server | `connected` 必定为 `true` |

### 6.2 连接状态轮询

```typescript
// 可选：定期检查连接状态
useEffect(() => {
  const interval = setInterval(async () => {
    const res = await fetch('/mcp/servers/list', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId }),
    });
    const data = await res.json();
    if (data.code === 1) {
      setServers(data.data);
    }
  }, 60_000); // 每分钟刷新一次

  return () => clearInterval(interval);
}, [userId]);
```

---

## 七、完整数据流总结

```
┌──────────────────────────────────────────────────────────────┐
│                        用户操作                               │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ① 添加 MCP Server                                           │
│     POST /mcp/servers                                        │
│     → 返回工具列表（全名格式，含 mcp_uuid_server_ 前缀）        │
│     → 工具自动注册到后端，但前端不展示前缀                       │
│                                                               │
│  ② 查看工具目录                                               │
│     POST /ai/tools           → 获取 BUILTIN 工具              │
│     POST /mcp/tools           → 获取 MCP 工具（已分组+displayName） │
│     POST /mcp/servers/list    → 获取 Server 列表及连接状态     │
│     → 前端按 Server 分组，displayName 直接展示                 │
│                                                               │
│  ③ 会话配置（选择工具）                                        │
│     → 用户勾选要启用的工具                                     │
│     → 保存 toolPreferences（全名格式，后端用）                  │
│     → 前端展示时去掉前缀                                       │
│                                                               │
│  ④ 发送消息                                                   │
│     POST /ai/chat { toolNames: [...全名...] }                 │
│     → SSE 流返回 tool_start/tool_end（工具名是全名）            │
│     → 前端在卡片中展示为 "Server名 / 工具名"                   │
│                                                               │
│  ⑤ 删除 Server                                                │
│     DELETE /mcp/servers/{name}                                │
│     → 工具自动注销，所有引用该 Server 的会话配置需同步清理       │
│                                                               │
│  ⑥ 注销用户                                                    │
│     POST /user/deactivate                                     │
│     → 禁用账号 + 清除令牌 + 删除 API 配置 + 清理所有 MCP Server  │
│     → 前端需清除本地 token，跳转到登录页                         │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

---

## 八、关键注意事项

| 注意点 | 说明 |
|--------|------|
| **永远不要向用户展示 `mcp_{userId}_` 前缀** | 用户不需要知道 userId 和内部路由格式 |
| **MCP 工具名含下划线时不要截断** | `get_weather_forecast` 是一个完整工具名，按 serverName 精确匹配拆分 |
| **删除 Server 后清理 toolPreferences** | 会话配置中可能残留已删除 Server 的工具名，需前端过滤 |
| **`enableTools=true` 不包含 MCP** | 这是设计决策，防止用户无意中把所有远程工具暴露给 AI |
| **连接状态是瞬时的** | 即使 `connected: true`，下一次调用时也可能因网络波动失败 |
| **MCP 工具参数 Schema 来自远端** | 不同 Server 的参数格式可能不同，前端渲染参数表单时需动态适配 |
