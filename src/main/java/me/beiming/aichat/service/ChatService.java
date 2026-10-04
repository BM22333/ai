package me.beiming.aichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.beiming.aichat.constant.ChatConstant;
import me.beiming.aichat.constant.MessageConstant;
import me.beiming.aichat.constant.RedisConstant;
import me.beiming.aichat.dto.ChatDTO;
import me.beiming.aichat.entity.Conversation;
import me.beiming.aichat.entity.ToolDefinition;
import me.beiming.aichat.entity.message.AssistantMessage;
import me.beiming.aichat.entity.message.Message;
import me.beiming.aichat.entity.message.ToolMessage;
import me.beiming.aichat.entity.message.UserMessage;
import me.beiming.aichat.entity.prompt.InsertPrompt;
import me.beiming.aichat.entity.user.UserInsertPrompt;
import me.beiming.aichat.mapper.ConversationMapper;
import me.beiming.aichat.mapper.UserInsertPromptMapper;
import me.beiming.aichat.toolCalling.ToolRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ChatService {

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private UserInsertPromptMapper userInsertPromptMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 非阻塞 HTTP 客户端，支持流式 SSE 读取。
     * 设置较长超时以适应 LLM 推理耗时（尤其是深度思考 + 工具调用场景）。
     */
    private final WebClient webClient = WebClient.builder()
            .codecs(config -> config.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                    HttpClient.create()
                            .responseTimeout(Duration.ofSeconds(300))   // 5 分钟读取超时
                            .keepAlive(true)))
            .build();

    /**
     * 普通聊天接口，SSE 流式输出。
     */
    public Flux<String> chat(ChatDTO dto) {
        return doChat(dto, false);
    }

    /**
     * 深度思考聊天接口，SSE 流式输出。
     */
    public Flux<String> deepThinkingChat(ChatDTO dto) {
        return doChat(dto, true);
    }


    /**
     * 统一聊天流程：
     * 参数校验 → 加载会话 & 历史 → 保存用户消息 → 解析工具 →
     * 构建 messages → 流式调用 AI → 转发 SSE → 完成后保存。
     *
     * @param dto      聊天请求
     * @param thinking 是否深度思考模式
     * @return SSE 事件流
     */
    private Flux<String> doChat(ChatDTO dto, boolean thinking) {
        String conversationId = dto.getConversationId();
        String userMessage = dto.getMessage();

        log.debug("[doChat] 入口: conversationId={}, thinking={}, enableTools={}, toolNames={}, messageLen={}",
                conversationId, thinking, dto.getEnableTools(), dto.getToolNames(),
                userMessage != null ? userMessage.length() : 0);

        // 1. 参数校验
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("会话ID不能为空"));
        }
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("消息不能为空"));
        }

        // 2. 从 MySQL 加载会话
        Conversation conversation = conversationMapper.selectByConversationId(conversationId);
        if (conversation == null) {
            return Flux.error(new IllegalArgumentException(MessageConstant.CONVERSATION_NOT_FOUND_ERROR));
        }

        // 3. 从 Redis 加载对话历史
        String redisKey = RedisConstant.CHAT_HISTORY + conversationId;
        @SuppressWarnings("unchecked")
        List<Message> history = (List<Message>) redisTemplate.opsForValue().get(redisKey);
        if (history == null) {
            history = new ArrayList<>();
        }

        // 4. 保存用户消息到 Redis（depth 默认 0，isSeeable 默认可见）
        Message userMsg = new UserMessage(userMessage);
        log.info("[深度追踪] doChat: 保存UserMessage, depth={}", userMsg.getDepth());
        history.add(userMsg);
        redisTemplate.opsForValue().set(redisKey, history);

        // 5. 解析工具定义
        List<Map<String, Object>> toolDefs = resolveToolDefinitions(dto, conversation);

        // 6. 解析本次请求要注入的提示词并构建 messages 集合
        List<InsertPrompt> insertPrompts = resolveInsertPrompts(dto.getInsertPromptIds());
        log.debug("[doChat] buildMessages: insertPrompts={}, maxMessages={}, afterHistoryPrompt={}",
                insertPrompts.size(), conversation.getMaxMessages(),
                conversation.getAfterHistoryPrompt() != null);
        List<Map<String, Object>> messages = PromptBuilder.buildOpenAiMessages(conversation, history, insertPrompts);

        // 7. 累积 AI 回复内容的容器
        StringBuilder contentAccumulator = new StringBuilder();
        StringBuilder thinkingAccumulator = new StringBuilder();
        List<Message.ToolCall> executedToolCalls = new ArrayList<>();

        // 记录最终 assistant 回复所在的工具调用轮次深度（无工具调用时为 0）
        int[] depthHolder = new int[1];

        // 8. 选择流策略：有工具 → 工具调用循环；无工具 → 直接流
        Flux<String> stream;
        if (!toolDefs.isEmpty()) {
            log.info("启用工具调用: conversationId={}, tools={}",
                    conversationId,
                    toolDefs.stream().map(t -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fn = (Map<String, Object>) t.get("function");
                        return fn.get("name");
                    }).toList());
            stream = chatWithToolsLoop(messages, toolDefs, dto, conversation, thinking,
                    0, executedToolCalls, depthHolder);
        } else {
            stream = streamChatCompletion(conversation, messages, null, thinking);
        }

        // 9. 累积 + 保存
        return stream
                .doOnNext(sseJson -> accumulateEvent(sseJson, contentAccumulator, thinkingAccumulator, thinking))
                .doOnComplete(() -> {
                    String contentBody = thinking
                            ? "<thinking>" + thinkingAccumulator + "</thinking>"
                            : "";
                    contentBody += contentAccumulator.toString();

                    if (contentBody.isEmpty() && executedToolCalls.isEmpty()) {
                        log.warn("AI 回复内容为空，跳过保存: key={}", redisKey);
                        return;
                    }

                    Message assistantMsg;
                    // 工具调用已在循环中逐条保存（assistant(tc) + tool(result)）
                    // 最终消息只保留文本内容，不重复附带 tool_calls
                    // 否则下一轮对话时 AI API 会报: insufficient tool messages following tool_calls message
                    String finalContent = contentBody.isEmpty() ? null : contentBody;
                    log.info("[深度追踪] doOnComplete: depthHolder[0]={}, executedToolCalls={}",
                            depthHolder[0], executedToolCalls.size());
                    assistantMsg = new AssistantMessage(finalContent, depthHolder[0]);
                    log.info("[深度追踪] doOnComplete: finalAssistantMsg depth={}", assistantMsg.getDepth());
                    saveMessage(redisKey, assistantMsg);
                    log.info("AI 回复已保存: key={}, toolCalls={}, contentLength={}",
                            redisKey, executedToolCalls.size(), contentBody.length());
                })
                .doOnError(e -> log.error("聊天流异常: conversationId={}", conversationId, e));
    }

    /**
     * 根据会话和请求参数解析最终生效的工具定义列表。
     *
     * <pre>
     * enableTools == false  → 返回空列表（工具被禁用）   仅当前端开启禁用全部工具时
     * enableTools == true   → 返回全部已注册工具   仅当前端开启允许AI使用全部工具时
     * enableTools == null   → 合并模式：会话级 toolPreferences ∪ 请求级 toolNames   一般情况和前端临时追加工具时
     * </pre>
     */
    List<Map<String, Object>> resolveToolDefinitions(ChatDTO dto, Conversation conversation) {
        Set<String> resolvedNames = resolveToolNames(dto, conversation);
        if (resolvedNames.isEmpty()) {
            return Collections.emptyList();
        }
        return toolRegistry.toOpenAiFormat(resolvedNames);
    }

    /**
     * 解析最终启用的工具名称集合。
     */
    private Set<String> resolveToolNames(ChatDTO dto, Conversation conversation) {
        Boolean enableTools = dto.getEnableTools();

        // 显式禁用
        if (Boolean.FALSE.equals(enableTools)) {
            return Collections.emptySet();
        }

        // 显式启用全部（仅 BUILTIN 工具，MCP 工具需显式指定）
        if (Boolean.TRUE.equals(enableTools)) {
            return new LinkedHashSet<>(toolRegistry.getBuiltinToolNames());
        }

        // 合并模式：会话级 ∪ 请求级
        Set<String> merged = new LinkedHashSet<>();

        // 会话级工具偏好
        Set<String> sessionTools = parseToolPreferences(conversation.getToolPreferences());
        if (sessionTools != null) {
            merged.addAll(sessionTools);
        }

        // 请求级工具名称
        List<String> requestTools = dto.getToolNames();
        if (requestTools != null && !requestTools.isEmpty()) {
            merged.addAll(requestTools);
        }

        return merged;
    }

    /**
     * 解析会话的 toolPreferences JSON 数组字符串。
     *
     * @param toolPreferences 如 "[\"calculator\",\"date_time\"]"，可为 null/空
     * @return 工具名称集合，解析失败返回空集合
     */
    private Set<String> parseToolPreferences(String toolPreferences) {
        if (toolPreferences == null || toolPreferences.trim().isEmpty()
                || "[]".equals(toolPreferences.trim())) {
            return Collections.emptySet();
        }
        try {
            JsonNode node = objectMapper.readTree(toolPreferences);
            Set<String> result = new LinkedHashSet<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    result.add(item.asText());
                }
            }
            return result;
        } catch (JsonProcessingException e) {
            log.warn("toolPreferences 解析失败: {}", toolPreferences, e);
            return Collections.emptySet();
        }
    }

    /**
     * 递归工具调用循环（流式版）。
     *
     * <p>关键设计：每轮不再用 {@code collectList()} 缓冲整轮 SSE 流（旧实现会导致工具调用
     * 轮次期间前端一片空白，所有内容要等全部轮次结束后才一次性到达），而是：
     * <ol>
     *   <li>{@code content} / {@code reasoning} / {@code error} 事件边到边透传给前端，实时渲染；</li>
     *   <li>{@code tool_calls} 事件只出现在 OpenAI 兼容流的末尾（{@code done} 之前），单独缓存、不转发；</li>
     *   <li>上游流结束后统一决策：有 tool_calls → 执行工具 → 追加消息 → 递归下一轮；
     *       无 tool_calls → 本轮为叶子轮次，记录深度，直接结束（内容已实时转发）。</li>
     * </ol>
     *
     * @param messages          OpenAI 兼容消息列表（会在递归中被修改）
     * @param toolDefs          OpenAI 格式工具定义列表
     * @param dto               请求 DTO
     * @param conversation      会话配置
     * @param thinking          是否深度思考
     * @param depth             本轮之前已完成的工具调用轮数（首次调用为 0）
     * @param allExecutedCalls  累计执行的所有工具调用（最终会被保存到 ASSISTANT 消息）
     * @param depthHolder       记录最终 assistant 回复所在深度（叶子分支赋值）
     * @return SSE 事件流
     */
    private Flux<String> chatWithToolsLoop(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> toolDefs,
            ChatDTO dto,
            Conversation conversation,
            boolean thinking,
            int depth,
            List<Message.ToolCall> allExecutedCalls,
            int[] depthHolder) {

        if (depth >= resolveMaxToolCallDepth(conversation)) {
            log.warn("工具调用深度已达上限: depth={}", depth);
            return Flux.just("{\"type\":\"error\",\"message\":\"工具调用次数过多，已终止\"}");
        }

        // 缓存本轮 AI 返回的 tool_calls 事件（位于流末尾、done 之前）
        AtomicReference<List<JsonNode>> toolCallEventsRef = new AtomicReference<>(new ArrayList<>());

        return streamChatCompletion(conversation, messages, toolDefs, thinking)
                // ---- 实时分流：content/reasoning 透传给前端，tool_calls 与 done 拦截 ----
                .flatMap(event -> {
                    try {
                        JsonNode node = objectMapper.readTree(event);
                        String type = node.get("type").asText();
                        if ("tool_calls".equals(type)) {
                            toolCallEventsRef.get().add(node);
                            return Flux.empty();
                        }
                        if ("done".equals(type)) {
                            return Flux.empty();
                        }
                        return Flux.just(event); // content / reasoning / error 实时转发
                    } catch (Exception e) {
                        return Flux.just(event); // 非 JSON 载荷透传，兼容旧格式
                    }
                })
                // ---- 上游流结束后统一决策（必须用 defer：订阅时才读取缓存，否则拿不到数据） ----
                .concatWith(Flux.defer(() -> {
                    List<JsonNode> toolCallNodes = toolCallEventsRef.get();
                    if (toolCallNodes.isEmpty()) {
                        // 叶子轮次：无工具调用，内容已实时转发，记录最终深度后结束
                        depthHolder[0] = depth;
                        log.info("[深度追踪] chatWithToolsLoop叶子节点: depth={}, depthHolder[0]已设为{}", depth, depth);
                        return Flux.empty();
                    }

                    // ---- 有工具调用：在 boundedElastic 线程池执行阻塞操作 ----
                    // MCP client.callTool() 和 Redis 写入都是阻塞的，不能在 reactor-http-nio 线程执行
                    log.info("检测到 {} 个工具调用事件, depth={}", toolCallNodes.size(), depth);

                    return Mono.fromCallable(() ->
                            executeToolCallsBlocking(toolCallNodes, allExecutedCalls, dto.getConversationId(), depth))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(roundCalls -> {
                                // ---- 追加消息到 messages 列表（为下一轮 AI 调用准备） ----
                                messages.add(buildAssistantToolCallMap(roundCalls));
                                for (Message.ToolCall tc : roundCalls) {
                                    messages.add(buildToolResultMap(tc));
                                }

                                // ---- 构建 SSE 事件 ----
                                List<String> toolSseEvents = new ArrayList<>();
                                for (Message.ToolCall tc : roundCalls) {
                                    toolSseEvents.add(buildToolStartEvent(tc.getFunctionName(), tc.getArguments()));
                                    toolSseEvents.add(buildToolEndEvent(tc.getFunctionName(), tc.getResult()));
                                }

                                // ---- 递归：继续对话 ----
                                return Flux.fromIterable(toolSseEvents).concatWith(
                                        chatWithToolsLoop(messages, toolDefs, dto, conversation, thinking,
                                                depth + 1, allExecutedCalls, depthHolder));
                            });
                }));
    }

    /**
     * 执行本轮所有工具调用并保存到 Redis（阻塞操作，运行在 boundedElastic 线程池）。
     *
     * @param allToolCallNodes  AI 返回的 tool_calls 事件节点
     * @param allExecutedCalls  累计列表（本方法会追加）
     * @param conversationId    会话 ID（用于 Redis key）
     * @param depth             当前工具调用轮次深度
     * @return 本轮执行的所有工具调用记录
     */
    private List<Message.ToolCall> executeToolCallsBlocking(
            List<JsonNode> allToolCallNodes,
            List<Message.ToolCall> allExecutedCalls,
            String conversationId,
            int depth) {

        List<Message.ToolCall> roundCalls = new ArrayList<>();

        for (JsonNode tcEvent : allToolCallNodes) {
            JsonNode callsNode = tcEvent.get("calls");
            if (callsNode == null || !callsNode.isArray()) continue;

            for (JsonNode call : callsNode) {
                String callId = call.get("id").asText();
                String funcName = call.get("function").get("name").asText();
                String arguments = call.get("function").get("arguments").asText();

                // 执行工具（MCP 为阻塞网络调用）
                String result = toolRegistry.execute(funcName, arguments);
                log.info("工具执行: name={}, args={}, resultLen={}", funcName, arguments,
                        result != null ? result.length() : 0);

                Message.ToolCall tc = new Message.ToolCall();
                tc.setId(callId);
                tc.setType("function");
                tc.setFunctionName(funcName);
                tc.setArguments(arguments);
                tc.setResult(result);
                roundCalls.add(tc);
                allExecutedCalls.add(tc);
            }
        }

        // 保存到 Redis 历史（阻塞 I/O）
        // depth 表示本轮之前已完成的工具调用轮数，当前是第 depth+1 轮
        int roundNumber = depth + 1;
        String redisKey = RedisConstant.CHAT_HISTORY + conversationId;
        Message assistantWithTools = AssistantMessage.withToolCalls(roundCalls, roundNumber);
        saveMessage(redisKey, assistantWithTools);
        for (Message.ToolCall tc : roundCalls) {
            Message toolMsg = ToolMessage.toolResult(tc.getId(), tc.getFunctionName(), tc.getResult(), roundNumber);
            saveMessage(redisKey, toolMsg);
        }

        return roundCalls;
    }

    /**
     * 构建 ASSISTANT 消息的 tool_calls 字段（OpenAI 格式）。
     */
    private Map<String, Object> buildAssistantToolCallMap(List<Message.ToolCall> toolCalls) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", "assistant");
        // content 必须为 null（而非 ""），部分 AI API 对此严格校验
        item.put("content", null);

        List<Map<String, Object>> tcList = new ArrayList<>();
        for (Message.ToolCall tc : toolCalls) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("id", tc.getId());
            call.put("type", "function");
            Map<String, String> fn = new LinkedHashMap<>();
            fn.put("name", tc.getFunctionName());
            fn.put("arguments", tc.getArguments());
            call.put("function", fn);
            tcList.add(call);
        }
        item.put("tool_calls", tcList);
        return item;
    }

    /**
     * 构建 TOOL 结果消息（OpenAI 格式）。
     */
    private Map<String, Object> buildToolResultMap(Message.ToolCall tc) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", "tool");
        item.put("tool_call_id", tc.getId());
        item.put("name", tc.getFunctionName());
        item.put("content", tc.getResult() != null ? tc.getResult() : "");
        return item;
    }

    /**
     * 构建 tool_start SSE 事件。
     */
    private String buildToolStartEvent(String toolName, String arguments) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "tool_start");
            event.put("tool", toolName);
            event.put("args", objectMapper.readTree(arguments));
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return "{\"type\":\"tool_start\",\"tool\":\"" + toolName + "\",\"args\":" + arguments + "}";
        }
    }

    /**
     * 构建 tool_end SSE 事件。
     */
    private String buildToolEndEvent(String toolName, String result) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "tool_end");
            event.put("tool", toolName);
            event.put("result", result);
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return "{\"type\":\"tool_end\",\"tool\":\"" + toolName + "\",\"result\":\"" + result + "\"}";
        }
    }

    /**
     * 累积 SSE 事件中的 content / reasoning 文本。
     */
    private void accumulateEvent(String sseJson, StringBuilder contentAcc,
                                  StringBuilder thinkingAcc, boolean thinking) {
        try {
            JsonNode node = objectMapper.readTree(sseJson);
            if (!node.has("type")) return;
            String type = node.get("type").asText();
            if ("content".equals(type) && node.has("text")) {
                contentAcc.append(node.get("text").asText());
            } else if (thinking && "reasoning".equals(type) && node.has("text")) {
                thinkingAcc.append(node.get("text").asText());
            }
            // tool_start, tool_end, error 不需要累积
        } catch (Exception e) {
            log.trace("SSE 事件累积跳过: {}", sseJson);
        }
    }

    /**
     * 保存一条消息到 Redis 历史。
     */
    private void saveMessage(String redisKey, Message message) {
        if (message.getContent() != null && message.getContent().isEmpty()) {
            message.setContent(null);
        }
        @SuppressWarnings("unchecked")
        List<Message> currentHistory = (List<Message>) redisTemplate.opsForValue().get(redisKey);
        if (currentHistory != null) {
            currentHistory.add(message);
            redisTemplate.opsForValue().set(redisKey, currentHistory);
            } else {
            log.warn("Redis 中未找到对话历史，跳过保存: key={}", redisKey);
        }
    }

    /**
     * 以流式（SSE）方式直连大模型 OpenAI 兼容 API（{@code {baseUrl}/v1/chat/completions}）。
     *
     * <p>把上游的 {@code chat.completion.chunk} 流转换为本项目内部的 SSE 事件协议：
     * <ul>
     *   <li>{@code content}    —— 正文增量，实时转发前端</li>
     *   <li>{@code reasoning}  —— 思考增量，仅深度思考模式转发</li>
     *   <li>{@code tool_calls} —— 工具调用，流末尾按 index 汇总为一个事件（由调用方拦截执行）</li>
     *   <li>{@code done}       —— 流正常结束标记</li>
     *   <li>{@code error}      —— 上游异常信息</li>
     * </ul>
     *
     * @param conversation 会话配置（含 apiKey、baseUrl、model）
     * @param messages     OpenAI 兼容的消息列表
     * @param toolDefs     工具定义列表（可为 null/空 = 不启用工具调用）
     * @param thinking     是否深度思考模式；为 true 时携带 reasoning_effort 并转发 reasoning 事件
     * @return SSE JSON 事件流
     */
    private Flux<String> streamChatCompletion(Conversation conversation,
                                              List<Map<String, Object>> messages,
                                              List<Map<String, Object>> toolDefs,
                                              boolean thinking) {

        String url;
        try {
            url = buildChatCompletionsUrl(conversation.getBaseUrl());
        } catch (IllegalArgumentException e) {
            return Flux.error(e);
        }

        // 构建 OpenAI 兼容请求体
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", conversation.getModel());
        requestBody.put("messages", messages);
        requestBody.put("stream", true);
        Double temperature = conversation.getTemperature();
        requestBody.put("temperature", temperature != null ? temperature : ChatConstant.DEFAULT_TEMPERATURE);
        if (thinking) {
            // 仅深度思考模式携带推理力度：普通模式不带该参数，避免被不支持它的模型拒绝
            String reasoningEffort = conversation.getReasoningEffort();
            requestBody.put("reasoning_effort",
                    reasoningEffort != null && !reasoningEffort.isBlank()
                            ? reasoningEffort : ChatConstant.DEFAULT_REASONING_EFFORT);
        }

        // 工具定义
        if (toolDefs != null && !toolDefs.isEmpty()) {
            requestBody.put("tools", toolDefs);
        }

        log.debug("[streamChatCompletion] url={}, model={}, thinking={}, tools={}",
                url, conversation.getModel(), thinking, toolDefs != null ? toolDefs.size() : 0);
        logMessageRoles(messages);

        // 每次订阅使用独立的工具调用累加器（上游按 index 分片下发 tool_calls）
        return Flux.defer(() -> {
            ToolCallAccumulator accumulator = new ToolCallAccumulator();

            return webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + conversation.getApiKey())
                    .bodyValue(requestBody)
                    .retrieve()
                    // 上游非 2xx：把响应体一起带出来，便于定位密钥/模型/参数问题
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> new UpstreamApiException("AI API 返回 "
                                    + response.statusCode().value() + ": " + abbreviate(body, 500))))
                    .bodyToFlux(String.class)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    // 上游以 "data: [DONE]" 结束
                    .takeWhile(line -> !isDoneMarker(line))
                    // 每个上游分片 → 0..2 个内部事件（content / reasoning）
                    .concatMap(chunk -> toInternalEvents(chunk, accumulator, thinking))
                    // 流正常结束后补发汇总的 tool_calls 与 done
                    .concatWith(Flux.defer(() -> Flux.fromIterable(tailEvents(accumulator))))
                    .doOnError(e -> log.error("AI API 流异常: url={}", url, e))
                    .onErrorResume(e -> Flux.just(buildErrorEvent(describeError(e))));
        });
    }

    /**
     * 拼接 OpenAI 兼容的 chat completions 端点。
     * baseUrl 形如 {@code https://api.siliconflow.cn} 或 {@code https://api.openai.com/v1}。
     */
    private String buildChatCompletionsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("会话未配置 Base URL");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.endsWith("/v1")
                ? normalized + "/chat/completions"
                : normalized + "/v1/chat/completions";
    }

    /**
     * 打印消息角色序列，方便排查 tool_calls 消息链问题。
     */
    private void logMessageRoles(List<Map<String, Object>> messages) {
        if (!log.isInfoEnabled()) {
            return;
        }
        StringBuilder roles = new StringBuilder("[streamChatCompletion] 消息序列: ");
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> m = messages.get(i);
            roles.append(i).append(":").append(m.get("role"));
            if (m.containsKey("tool_calls") && m.get("tool_calls") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tcs = (List<Map<String, Object>>) m.get("tool_calls");
                roles.append("(tc=").append(tcs.size()).append(")");
            }
            String toolCallId = (String) m.get("tool_call_id");
            if (toolCallId != null) {
                roles.append("(tcid=").append(toolCallId).append(")");
            }
            if (i < messages.size() - 1) {
                roles.append(" → ");
            }
        }
        log.info(roles.toString());
    }

    /**
     * 把一个上游分片转换为内部 SSE 事件（content / reasoning），并同步累加 tool_calls。
     *
     * <p>标准流式响应放在 {@code choices[0].delta}；若上游忽略 {@code stream=true} 而直接返回
     * 完整结果，则退化为读取 {@code choices[0].message}。
     */
    private Flux<String> toInternalEvents(String chunk, ToolCallAccumulator accumulator, boolean thinking) {
        JsonNode root;
        try {
            root = objectMapper.readTree(chunk);
        } catch (Exception e) {
            log.trace("忽略无法解析的上游数据块: {}", chunk);
            return Flux.empty();
        }

        JsonNode choice = root.path("choices").path(0);
        if (choice.isMissingNode() || choice.isNull()) {
            // 部分服务会在流中插入 usage / 心跳等无 choices 的数据块
            log.trace("忽略无 choices 的上游数据块: {}", chunk);
            return Flux.empty();
        }
        JsonNode payload = choice.has("delta") ? choice.path("delta") : choice.path("message");

        List<String> events = new ArrayList<>();
        // reasoning_content 是 OpenAI 兼容生态的通行字段，reasoning 为部分网关的别名
        String reasoning = textOf(payload.get("reasoning_content"));
        if (reasoning.isEmpty()) {
            reasoning = textOf(payload.get("reasoning"));
        }
        if (thinking && !reasoning.isEmpty()) {
            events.add(buildTextEvent("reasoning", reasoning));
        }
        String content = textOf(payload.get("content"));
        if (!content.isEmpty()) {
            events.add(buildTextEvent("content", content));
        }
        JsonNode toolCalls = payload.path("tool_calls");
        if (toolCalls.isArray()) {
            for (int i = 0; i < toolCalls.size(); i++) {
                accumulator.merge(toolCalls.get(i), i);
            }
        }
        return Flux.fromIterable(events);
    }

    /**
     * 流正常结束后补发的事件：汇总的 tool_calls（若有）与 done。
     */
    private List<String> tailEvents(ToolCallAccumulator accumulator) {
        List<String> events = new ArrayList<>();
        List<Map<String, Object>> calls = accumulator.toOpenAiCalls();
        if (!calls.isEmpty()) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "tool_calls");
            event.put("calls", calls);
            events.add(toJson(event));
        }
        events.add("{\"type\":\"done\"}");
        return events;
    }

    /**
     * 构建 content / reasoning 文本事件。
     */
    private String buildTextEvent(String type, String text) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("text", text);
        return toJson(event);
    }

    /**
     * 构建 error 事件。
     */
    private String buildErrorEvent(String message) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "error");
        event.put("message", message);
        return toJson(event);
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // 事件仅含字符串，正常不会失败；兜底避免整条流被中断
            log.error("SSE 事件序列化失败: {}", event, e);
            return "{\"type\":\"error\",\"message\":\"内部事件序列化失败\"}";
        }
    }

    /**
     * 判断上游数据块是否为流结束标记 {@code [DONE]}。
     */
    private boolean isDoneMarker(String payload) {
        return "[DONE]".equals(payload);
    }

    /**
     * 把上游异常转换为给前端的错误文案。
     */
    private String describeError(Throwable e) {
        if (e instanceof UpstreamApiException) {
            return e.getMessage();
        }
        String msg = e.getMessage();
        if (e instanceof reactor.netty.http.client.PrematureCloseException
                || (msg != null && msg.contains("prematurely closed"))) {
            return "AI 服务响应中断，请稍后重试。若持续出现，请检查 AI API 密钥是否有效或模型是否可用。";
        }
        if (e instanceof java.util.concurrent.TimeoutException) {
            return "AI 服务响应超时，请稍后重试。";
        }
        return "AI 服务请求失败: " + (msg != null ? msg : e.getClass().getSimpleName());
    }

    /**
     * 读取文本字段；缺失、null 或非文本时返回空串（NullNode.asText() 会返回 "null"，故须显式判断）。
     */
    private static String textOf(JsonNode node) {
        return (node == null || node.isMissingNode() || node.isNull()) ? "" : node.asText("");
    }

    /**
     * 截断过长的响应体，避免日志与错误事件被刷屏。
     */
    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max) + "...";
    }

    /**
     * 累加 OpenAI 流式响应中按 index 分片下发的 tool_calls：首个分片带 id/name，
     * 后续分片只带 arguments 片段，必须按 index 拼接。
     */
    private static final class ToolCallAccumulator {

        private final Map<Integer, Fragment> fragments = new LinkedHashMap<>();

        void merge(JsonNode callNode, int fallbackIndex) {
            int index = callNode.hasNonNull("index") ? callNode.get("index").asInt() : fallbackIndex;
            Fragment fragment = fragments.computeIfAbsent(index, k -> new Fragment());

            String id = textOf(callNode.get("id"));
            if (!id.isEmpty()) {
                fragment.id = id;
            }
            JsonNode function = callNode.get("function");
            if (function != null && function.isObject()) {
                String name = textOf(function.get("name"));
                if (!name.isEmpty()) {
                    fragment.name = name;
                }
                fragment.arguments.append(textOf(function.get("arguments")));
            }
        }

        /**
         * 按 index 顺序输出 OpenAI 格式的 tool_calls 数组；缺少函数名的碎片视为无效并丢弃。
         */
        List<Map<String, Object>> toOpenAiCalls() {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Fragment fragment : fragments.values()) {
                if (fragment.name == null) {
                    continue;
                }
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", fragment.name);
                function.put("arguments", fragment.arguments.toString());

                Map<String, Object> call = new LinkedHashMap<>();
                call.put("id", fragment.id != null ? fragment.id : "call_" + result.size());
                call.put("type", "function");
                call.put("function", function);
                result.add(call);
            }
            return result;
        }

        private static final class Fragment {
            private String id;
            private String name;
            private final StringBuilder arguments = new StringBuilder();
        }
    }

    /**
     * 上游大模型 API 返回非 2xx 状态码时抛出，携带状态码与响应体摘要。
     */
    private static class UpstreamApiException extends RuntimeException {
        UpstreamApiException(String message) {
            super(message);
        }
    }

    /**
     * 解析本次聊天请求要注入的提示词。
     * 缺失/非法的 id 记录 warn 并跳过，不影响对话。
     *
     * @param insertPromptIds 请求携带的提示词 id 列表
     * @return 注入提示词列表（按请求 id 顺序，depth 为 null 时取 0）
     */
    private List<InsertPrompt> resolveInsertPrompts(List<Long> insertPromptIds) {
        if (insertPromptIds == null || insertPromptIds.isEmpty()) {
            return List.of();
        }
        List<UserInsertPrompt> prompts = userInsertPromptMapper.selectBatchIds(insertPromptIds);
        if (prompts == null || prompts.isEmpty()) {
            return List.of();
        }
        // selectBatchIds 不保证顺序，按请求 id 顺序重新排列
        Map<Long, UserInsertPrompt> byId = new LinkedHashMap<>();
        for (UserInsertPrompt p : prompts) {
            byId.put(p.getId(), p);
        }
        List<InsertPrompt> result = new ArrayList<>();
        for (Long id : insertPromptIds) {
            UserInsertPrompt p = byId.get(id);
            if (p == null) {
                log.warn("[doChat] 注入提示词不存在，跳过: id={}", id);
                continue;
            }
            result.add(InsertPrompt.of(p.getContent(), p.getDepth() != null ? p.getDepth() : 0));
        }
        return result;
    }

    /**
     * 解析会话的最大工具调用深度，null（旧会话行）时回退默认值。
     */
    private int resolveMaxToolCallDepth(Conversation conversation) {
        return (conversation.getMaxToolCallDepth() != null)
                ? conversation.getMaxToolCallDepth() : ChatConstant.DEFAULT_MAX_TOOL_CALL_DEPTH;
    }

    /**
     * 获取所有已注册工具的定义列表，供前端展示。
     */
    public List<me.beiming.aichat.dto.ToolDefinitionDTO> getToolDefinitions() {
        List<me.beiming.aichat.dto.ToolDefinitionDTO> result = new ArrayList<>();
        for (ToolDefinition def : toolRegistry.getAllTools()) {
            me.beiming.aichat.dto.ToolDefinitionDTO dto = new me.beiming.aichat.dto.ToolDefinitionDTO();
            dto.setName(def.getName());
            dto.setDescription(def.getDescription());
            dto.setProviderType(def.getProviderType());
            result.add(dto);
        }
        return result;
    }
}
