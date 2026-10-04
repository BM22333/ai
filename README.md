# AIAgent · 自托管 AI 对话平台

一个「Spring Boot 后端 + Vue 3 前端」的自托管 AI 聊天平台。用户自带 OpenAI 兼容的 API Key（SiliconFlow / DeepSeek / OpenAI / 任意兼容网关），可以创建多个会话，获得流式回复、深度思考、函数调用（内置工具 + MCP 远程工具），并自带会话、消息、提示词的管理能力。

- **大模型调用方式**：后端用 `WebClient` **直连**各家的 OpenAI 兼容接口（`POST {baseUrl}/v1/chat/completions`），中间不经过任何代理服务，API Key / Base URL / 模型都按「会话」维度配置。
- **技术栈**：Java 17 · Spring Boot 3.5.5 · WebFlux(WebClient) · MyBatis-Plus · MySQL · Redis · Vue 3 · TypeScript · Vite · Element Plus

---

## 一、功能特性

### 1. 对话
- SSE 流式输出，逐字渲染；上游 `content` 增量边到边透传。
- 支持任意 OpenAI 兼容服务，只要填 Base URL + API Key + 模型名。
- 温度（`temperature`）按会话配置，缺省 1.0。
- 会话可选配置系统提示词、历史后提示词、展示给 AI 的最大消息数。

### 2. 深度思考
- 独立接口 `/ai/chat/deep-thinking`，前端切换「深度思考」开关即走该接口。
- 携带 `reasoning_effort`（low/medium/high，按会话配置），并把上游的 `reasoning_content` 作为 `reasoning` 事件推给前端，前端折叠成「思考过程」区块展示。
- 非深度思考模式不带 `reasoning_effort`，也不下发 `reasoning` 事件。

### 3. 工具调用（Function Calling）
- 两种工具来源：
  - **内置工具**：`@ToolDef` 注解声明，启动时由 `ToolRegistry` 扫描注册。现内置 `calculator`（数学表达式求值）与 `date_time`（日期时间查询）。
  - **MCP 远程工具**：用户可添加 MCP Server（HTTP 端点 + 自定义请求头），工具名会被规范化为 API 安全名并在执行时映射回原名。
- 多轮工具调用循环：AI 请求工具 → 后端执行 → 结果回灌 → 继续对话，直到 AI 给出最终文本回复或达到「最大工具调用深度」上限。
- 前端能实时看到「AI 思考 → 调用工具（参数）→ 工具返回（结果）→ AI 回复」全过程。
- 工具启用策略：请求可显式禁用（`enableTools=false`）、显式启用全部内置工具（`enableTools=true`），或默认「会话级工具偏好 ∪ 请求级工具名」合并。

### 4. 会话与消息管理
- 会话：创建 / 连接校验 / 删除 / 我的会话列表 / 部分字段更新（名称、系统提示词、模型、API Key、Base URL、工具偏好、最大消息数、最大工具深度、温度、推理力度）。
- 消息：编辑某条（后续消息一并截断）、删除单条、批量隐藏 / 取消隐藏（隐藏后 AI 看不到，但前端仍能拿到并渲染隐藏态）、按时间戳回溯（删除该时间点之后的所有消息）。

### 5. 提示词注入
- 可维护一套「插入提示词」（`user_insert_prompt` 表），每条带 `depth`。
- 发起对话时指定要注入的提示词 id，`PromptBuilder` 会把它们插入到「历史中第一条 depth 相同的消息之前」，无匹配则追加到历史末尾。

### 6. 用户与鉴权
- 注册 / 登录 / 刷新令牌 / 注销；密码以两次 MD5（无盐）存储。
- JWT 双令牌（access 30 分钟 / refresh 1 天），令牌同时写 Redis 白名单，注销即失效。
- 登录失败次数限制：5 次失败锁定 30 分钟（窗口 5 分钟）。
- Frontend：401 自动用 refreshToken 换新 accessToken 后重试。

### 7. API 配置管理
- 用户可保存多套「配置名 + API Key + Base URL + 模型名」。
- 保存前会拉取 `{baseUrl}/v1/models` 校验 Key 是否可用、模型是否存在于列表。

---

## 二、项目结构

```
AIAgent/
├── pom.xml                       Maven 构建（Spring Boot 3.5.5 / Java 17）
├── src/main/java/me/beiming/aichat/
│   ├── AiChatApplication.java    启动类
│   ├── controller/               REST 入口（见「接口一览」）
│   ├── service/                  业务编排，核心逻辑都在这里
│   ├── entity/                   数据库实体 + 领域对象
│   │   ├── message/              Message 及 USER/ASSISTANT/TOOL/SYSTEM 子类
│   │   ├── prompt/               系统提示词 / 历史提示词 / 历史后提示词 / 插入提示词
│   │   └── user/                 User / UserApi / UserConversation / UserInsertPrompt
│   ├── dto/                      请求体对象（每个接口一个）
│   ├── mapper/                   MyBatis-Plus Mapper（注解 SQL，无 XML）
│   ├── toolCalling/              工具系统
│   │   ├── ToolRegistry.java     扫描 @ToolDef、注册/注销工具、执行工具、转 OpenAI 格式
│   │   ├── BuiltinTools.java     内置工具实现
│   │   ├── McpServerManager.java MCP 连接管理、工具发现、健康检查
│   │   └── HeaderInjectingRequestCustomizer.java  MCP 自定义请求头注入
│   ├── tool/                     JwtTool（签发/解析 JWT）、SessionTool（Caffeine 缓存，见「问题」）
│   ├── annotation/               @ToolDef / @ToolParamDef
│   ├── config/                   Jwt / Redis / WebMvc(CORS+拦截器) / Swagger / Http / 登录安全 / MCP 恢复
│   ├── constant/                 ChatConstant / RedisConstant / MessageConstant / RoleConstant
│   ├── handler/                  GlobalExceptionHandler
│   └── interceptor/              JwtUserInterceptor（JWT 校验）
├── src/main/resources/
│   ├── application.yaml          主配置（数据源、Redis、JWT、MyBatis-Plus、Swagger、Tomcat）
│   └── application-dev.yaml      dev 配置（密码、密钥、登录安全阈值）— 见「问题」第 2 条
└── vue/                          前端工程
    ├── src/views/                Login / Register / Chat / ApiConfig / McpServers / SystemPromptEditor
    ├── src/components/           对话框、思考块、工具调用卡片、提示词选择器、批量隐藏等
    ├── src/api/                  axios + fetch 封装（auth / user / conversation / mcp / insertPrompt）
    ├── src/router/               路由 + 登录守卫
    ├── src/types/                前端类型定义
    ├── src/utils/thinking.ts     思考内容解析
    └── docs/                     前端适配指南（SSE 协议、工具调用、消息与提示词）
```

### 关键类

| 类 | 职责 |
| --- | --- |
| `service/ChatService` | 聊天主流程：加载历史 → 构建 messages → 直连大模型流式接口 → 工具调用循环 → 保存回复 |
| `service/PromptBuilder` | 纯静态工具类，把系统提示词 / 历史 / 插入提示词 / 历史后提示词组装成 OpenAI 格式，并做消息链清理 |
| `service/ConversationService` | 会话 CRUD、消息编辑/删除/隐藏/回溯 |
| `service/UserService` | 登录、注册、刷新、注销、API 配置与模型校验 |
| `service/McpService` | MCP Server 的增删查、重连、工具列表 |
| `toolCalling/ToolRegistry` | 工具注册表 + 执行入口（内置走反射，MCP 走远程调用） |

---

## 三、核心流程

### 1. 一次对话的完整链路

```
前端 POST /ai/chat（或 /ai/chat/deep-thinking）
        │  header: beiming = <accessToken>
        ▼
JwtUserInterceptor 校验令牌
        ▼
ChatService.doChat()
  1. 校验 conversationId / message 非空
  2. MySQL 按 conversationId 查会话配置（apiKey / baseUrl / model / 提示词 / 上限）
  3. Redis 读 chat:history:<conversationId>（List<Message>）
  4. 追加并保存本次用户消息（depth=0、isSeeable=true）
  5. 解析生效的工具定义
  6. 查插入提示词 → PromptBuilder 组装 messages
  7. 无工具 → 直接流；有工具 → chatWithToolsLoop 工具调用循环
        ▼
  POST {baseUrl}/v1/chat/completions   (Authorization: Bearer <apiKey>, stream=true)
        ▼
  上游 SSE 分片 → 翻译成本项目内部事件 → 透传前端
        ▼
  流结束：累积正文（深度思考模式额外把思考过程包成 <thinking>…</thinking>）
          → 写入 Redis 历史
```

### 2. SSE 事件协议

后端对所有事件统一以 `data: {json}` 下发。

| 事件 | 产生方 | 含义 | 是否转发前端 |
| --- | --- | --- | --- |
| `content` | 上游大模型 | 正文增量 | ✅ 直接展示 |
| `reasoning` | 上游大模型 | 思考增量（仅深度思考模式） | ✅ 折叠展示 |
| `tool_start` | 后端 | 工具开始执行（含参数） | ✅ 状态提示 |
| `tool_end` | 后端 | 工具执行完成（含结果） | ✅ 状态提示 |
| `error` | 后端 | 错误信息 | ✅（前端目前仅打日志，见「问题」） |
| `tool_calls` | 后端 | 本轮工具调用汇总 | ❌ 内部消费 |
| `done` | 后端 | 流正常结束标记 | ❌ 内部消费 |

事件样例：

```json
{ "type": "content",   "text": "计" }
{ "type": "reasoning", "text": "我需要先计算" }
{ "type": "tool_start", "tool": "calculator", "args": { "expression": "2+3*4" } }
{ "type": "tool_end",   "tool": "calculator", "result": "14" }
{ "type": "error",      "message": "AI API 返回 401: ..." }
```

### 3. 工具调用循环与 `depth`

- 每轮上游流结束后，若有 `tool_calls`：执行工具 → 把 `assistant(tool_calls)` 与 `tool` 结果消息追加到本轮 messages 并写入 Redis → 递归发起下一轮。
- 工具执行（MCP 网络调用、Redis 写入）是阻塞操作，跑在 `Schedulers.boundedElastic()` 上，避免堵住 reactor-http-nio 线程。
- 深度上限由会话的 `maxToolCallDepth` 决定，超限中止并返回 `error` 事件。
- `Message.depth` 语义：用户消息 = 0；第 r 轮工具调用的 `assistant(tc)` + `tool` = r；最终 assistant 回复 = 其所在轮次。前端「隐藏工具消息」正是按 depth 配对隐藏。

### 4. 历史消息的组装与清理（`PromptBuilder`）

顺序固定为：**系统提示词（最前）→ 历史 → 历史后提示词（最后）**，其中历史部分四步走：

1. 过滤 `isSeeable=false` 的消息；
2. **孤儿清理**（不动点迭代）：`tool` 消息前一条必须紧跟「携带 `tool_calls` 的 assistant」，携带 `tool_calls` 的 assistant 后面必须紧跟 `tool`，否则丢弃——因为 OpenAI 兼容接口对消息链配对是强校验的；
3. 截断保留最后 `maxMessages` 条，并处理窗口边界（首条是 `tool` 就前移丢弃，末条是带 `tool_calls` 的 assistant 就后移丢弃）；
4. 截断可能制造新孤儿，再跑一次孤儿清理。

### 5. 鉴权流程

- 前端把 accessToken 放在名为 `beiming` 的请求头（由 `jwt.config.token-name` 配置）。
- 拦截器校验 JWT 签名，并确认 Redis 中 `jwt:id:<userId>` 仍存在（注销/禁用即失效）。
- 令牌缺失 → 403；令牌过期 → 401，前端拦截 401 自动刷新后重试。
- 放行白名单：`/user/login`、`/user/register`、`/user/refresh` 与 Swagger 相关路径。

---

## 四、数据存储

### MySQL（库名 `ai_chat`）

| 表 | 说明 |
| --- | --- |
| `user` | 用户（用户名、MD5 密码、状态、时间） |
| `conversation` | 会话配置：模型、系统提示词、api_key、base_url、工具偏好、max_messages、max_tool_call_depth、after_history_prompt、temperature、reasoning_effort |
| `user_conversation` | 用户 ↔ 会话关系（用于「我的会话」列表与连接校验） |
| `user_api` | 用户的 API 配置（配置名、api_key、base_url、模型名）；表名由 MyBatis-Plus 驼峰转下划线推导 |
| `user_insert_prompt` | 用户插入提示词（名称、内容、depth） |
| `mcp_server` | 用户维度 MCP Server（名称、baseUrl、自定义请求头 JSON） |
| `spring_ai_chat_memory` | Spring AI ChatMemory 表，仅被 `ConversationService.rebuildChatMemory()` 写入（见「问题」第 4 条） |

### Redis

| Key | 用途 |
| --- | --- |
| `chat:history:<conversationId>` | 完整聊天历史（`List<Message>`，含 depth / isSeeable / toolCalls） |
| `jwt:access:<userId>` / `jwt:refresh:<userId>` | 令牌白名单（带 TTL） |
| `jwt:id:<userId>` | 用户对象缓存，同时作为令牌有效性标记 |
| `login:fail:<username>` / `login:locked:<username>` | 登录失败计数与锁定标记 |

### `Message` 对象

| 字段 | 说明 |
| --- | --- |
| `role` | `USER` / `ASSISTANT` / `TOOL` / `SYSTEM` |
| `content` | 文本内容；TOOL 角色时是工具返回值 |
| `depth` | 消息深度（见上），用于工具消息配对与隐藏 |
| `isSeeable` | AI 是否可见（null 兼容旧数据视为可见） |
| `toolCalls` | ASSISTANT 角色非空 = 请求调用工具（id / type / functionName / arguments） |
| `toolCallId` / `toolName` | TOOL 角色关联的调用 id 与函数名 |
| `timestamp` | 用于按时间戳回溯 |

---

## 五、接口一览

所有接口（除白名单外）都需要 `beiming` 请求头。

### 对话 `/ai`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/ai/chat` | 聊天（SSE 流式，支持工具调用） |
| POST | `/ai/chat/deep-thinking` | 深度思考聊天（SSE 流式） |
| POST | `/ai/tools` | 获取所有可用工具定义 |

### 会话 `/ai/conversation`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/create` `/connect` `/delete` | 创建 / 连接校验 / 删除会话 |
| POST | `/getHistory` `/getAll` | 取历史消息 / 取我的会话列表 |
| POST | `/update` | 部分更新会话配置（仅非 null 字段） |
| POST | `/rollback` | 按时间戳回溯，删除该时间点之后的消息 |
| POST | `/editMessage` `/deleteMessage` | 编辑（截断后续）/ 删除单条消息 |
| POST | `/hideMessages` `/showMessages` | 批量隐藏 / 取消隐藏 |

### 提示词 `/ai/insertPrompt`

`/create`、`/update`、`/delete`、`/list`

### 用户 `/user`

`/login`、`/register`、`/refresh`、`/models`（拉取上游模型列表）、`/config`（保存 API 配置）、`/api-config`（查询）、`/config/update`、`/deactivate`（注销）

### MCP `/mcp`

POST `/servers`（添加）、DELETE `/servers/{name}`、POST `/servers/list`、POST `/servers/{name}/reconnect`、POST `/tools`（工具列表，按 Server 分组）、POST `/servers/{name}/health`、DELETE `/servers`（清空该用户全部）

接口文档：启动后访问 `http://localhost:8080/swagger-ui.html`。

---

## 六、快速开始

### 环境要求

| 依赖 | 版本 |
| --- | --- |
| JDK | 17+ |
| Maven | 3.9+ |
| MySQL | 8.x（库名 `ai_chat`） |
| Redis | 6+ |
| Node.js | 18+（前端） |

### 1. 准备数据库

> 仓库内**没有**版本化的建表脚本（详见「问题」第 9 条），需要按上文「数据存储」的表清单自行建表。
> 上一份增量脚本只以构建残留的形式存在于 `target/classes/db/schema-update.sql`（给 `conversation` 补列 + 建 `user_insert_prompt`），可用作参考。

### 2. 修改配置

编辑 `src/main/resources/application-dev.yaml`，至少改这几项：`datasource.password`、`redis.password`、`jwt.config.secret`、`openai.api-key`。
`application.yaml` 里的数据源地址、Redis 地址、MyBatis-Plus 与 Swagger 配置按需调整（当前 MySQL 指向 `localhost:3306/ai_chat`）。

### 3. 启动后端

```bash
mvn spring-boot:run
# 或
mvn clean package && java -jar target/AIAgent-0.0.1-SNAPSHOT.jar
```

服务默认 `http://localhost:8080`。

### 4. 启动前端

```bash
cd vue
npm install
npm run dev
```

> 前端把后端地址硬编码成了 `http://localhost:8080`（见「问题」第 10 条），换端口/换机器需要改代码。

### 5. 上手流程

注册 → 登录 → 到「API 配置」填一套 API Key / Base URL / 模型（保存时会自动校验）→ 创建会话（选模型、可选填系统提示词）→ 开始对话，按需开启工具或深度思考。

---

## 七、已知问题与风险

> 以下均为阅读当前代码得出的结论，按严重程度排列。**「高」级别的几条建议优先处理。**

### 高

**1. 横向越权（IDOR）：只校验「登录了」，不校验「是不是你的」**

`JwtUserInterceptor` 只校验 JWT 签名与 Redis 中的用户标记，**没有把认证出来的 userId 放进请求上下文**；而各业务接口所需要的 `userId` 全部来自请求体（`UserIdDTO`）。这意味着任何已登录用户，只要把请求体里的 `userId` 换成别人的，就能读到：

- `/ai/conversation/getAll` → 别人的会话列表；
- `/user/api-config` → 别人的 API Key 与 Base URL；
- `/ai/insertPrompt/list` → 别人的提示词；
- `/mcp/servers/list` → 别人的 MCP 配置。

同样地，`/ai/conversation/getHistory`、`/ai/conversation/rollback`、`/ai/chat` 只按 `conversationId` 取数据，不校验该会话是否属于当前用户；`ChatService.resolveInsertPrompts()` 也是直接按 id 批量查提示词，不校验归属。

*建议*：在拦截器里解析出 userId 后写入 `request.setAttribute`（或 `ThreadLocal`/`UserContext`），业务层一律用服务端身份，不再信任请求体里的 userId；按会话查询时统一叠加归属校验。

**2. 凭据管理：密钥明文落库回传、口令弱哈希**

- `UserApi.apiKey` 没有任何脱敏或加密，`/user/api-config` 会把 apiKey 原样返回给前端；`Conversation.apiKey` 虽然标了 `@JsonIgnore`，但在 MySQL 里同样是明文。
- `src/main/resources/application-dev.yaml` 中硬编码了可直接使用的 `openai.api-key`、MySQL 密码、Redis 密码和 JWT secret，且已经提交进 Git。
- 用户口令使用两次无盐 MD5（`UserService.login/register`），既没有随机盐也没有慢哈希，容易被彩虹表 / GPU 爆破。

*建议*：密钥加解密或至少接入外部配置/环境变量；口令换成 BCrypt / Argon2；已泄露的 Key 立即轮换；把配置文件改为 `*.example` 模板并加入 `.gitignore`。

**3. CORS 配置过于宽松**

`WebMvcConfig` 使用 `allowedOriginPatterns("*")` 并且 `allowCredentials(true)`，等价于允许任意站点携带凭据访问后端。

*建议*：改为显式白名单（前端实际域名/端口）。

### 中

**4. 两套会话历史，只有一套在用（Spring AI 遗留）**

聊天链路只读 Redis；而 `ConversationService.rebuildChatMemory()` 在回溯/编辑/删除消息时又往 MySQL 的 Spring AI ChatMemory 表重写一份。问题是这份 MySQL 历史**没有任何地方读它**，而且重建逻辑只处理 `USER`/`ASSISTANT`，会丢掉 `TOOL` 消息与 `tool_calls`——即使将来有人去读，也必然与 Redis 不一致。

同源的历史遗留还有：`spring-ai-*` 依赖与 `spring.ai.openai` 配置（`ChatService` 现在直连大模型，不再需要）、`tool/SessionTool`（Caffeine 缓存，全项目无人调用）、`dto/CreateEmbeddingDTO`（只有 DTO，没有向量检索实现）。

*建议*：明确以 Redis 为唯一历史来源，删掉 `rebuildChatMemory` 与相关依赖/配置/死代码。

**5. Redis 聊天历史永不自动过期**

`chat:history:*` 只在「删除会话」时清理。除此之外既没有 TTL，也没有归档，长期运行会让 Redis 内存持续增长。

*建议*：写入历史时设置合理 TTL 并在活跃对话中续期，或增加冷数据归档。

**6. 同一会话的并发写会丢消息**

历史是「读整个 List → 追加 → 整体写回」（`doChat` 保存用户消息、`saveMessage()` 保存 AI 回复都是如此，`ConversationService` 的消息编辑同样）。同一会话内两个请求并发时，后写入的会覆盖前者，导致消息丢失。

*建议*：改为 Redis List 的原子追加（`RPUSH`），或对会话加分布式锁 / 用 `WATCH-MULTI` 乐观锁。

**7. 深度思考内容污染上下文**

深度思考模式下，思考过程被拼成 `<thinking>…</thinking>` 前缀**存进 assistant 消息的 content**（见 `ChatService.doChat` 的 `doOnComplete`）。下次对话时这段标签会被原样发回给模型，既污染上下文又白烧 token。

*建议*：思考内容在 `Message` 上单独存字段（如 `reasoning`），展示时再合并，回灌给 AI 时剔除。

**8. 没有任何测试**

`src/test` 不存在，78 个源文件零测试覆盖。最容易回归的两块——SSE 事件协议翻译、`PromptBuilder` 的消息链孤儿清理/截断——目前只能靠手工验证。

*建议*：优先给 `PromptBuilder`（纯函数，最好测）和事件翻译逻辑补单元测试。

### 低 / 工程细节

**9. 数据库结构没有版本化脚本**

`src/main/resources/db/` 已从仓库中消失，只有 `target/classes/db/schema-update.sql` 这份上一次构建的残留副本还留着，新环境无法从仓库一键建库。

*建议*：把 DDL 放回 `src/main/resources/db/`（或引入 Flyway/Liquibase），并把 `target/` 加进 `.gitignore`。

**10. 前端后端地址硬编码**

`http://localhost:8080` 直接写在 `vue/src/api/auth.ts` 与 `vue/src/api/conversation.ts` 里，没有走 Vite 代理或环境变量，换端口、换机器、部署都要改代码。

*建议*：抽成 `import.meta.env.VITE_API_BASE_URL`，并在 `vite.config.ts` 配 dev 代理。

**11. 错误事件前端不展示**

后端会把上游错误（API Key 无效、模型不存在、超时等）以 `{"type":"error","message":"..."}` 推给前端，但 `Chat.vue` 在 `case 'error'` 分支里只 `console.error`，用户看到的是一个「空气泡」回复。

*建议*：把 message 渲染成错误提示（toast 或气泡内红字）。

**12. 请求线程里阻塞调用上游**

`UserService.fetchModels()` 用 `bodyToMono(String.class).block(Duration.ofSeconds(10))` 同步等待，与项目其它地方的响应式风格不一致；上游慢时会一直占用 Tomcat 线程。

*建议*：改为异步或至少加连接/读取超时与降级提示。

**13. 工具调用循环每轮重发完整上下文**

`chatWithToolsLoop` 每轮都会把完整 messages 重新发给模型，多轮工具调用 + 长历史时 token 成本增长较快（有 `maxToolCallDepth` 与 `maxMessages` 兜底，但两者是独立限制）。

*建议*：结合场景调整两者默认值，或对工具结果做长度截断。

### 开发环境注意（非代码问题）

- **离线构建**：`mvn compile` 可离线完成，但 `mvn package` 在未预热本地仓库时会因 `com.mysql:mysql-connector-j:9.4.0`、`org.hdrhistogram:HdrHistogram:2.2.2` 未下载而失败，需要联网 `mvn dependency:go-offline`。
- **Windows 中文用户名 + `.m2` 路径**：若用脚本把依赖路径拼进 `javac` / `java` 的 `-cp`，含中文的 `.m2` 路径会在命令行编码上出问题（表现为「找不到包」），需要先复制到纯 ASCII 目录再使用。
