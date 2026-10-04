package me.beiming.aichat.controller;

import lombok.extern.slf4j.Slf4j;
import me.beiming.aichat.dto.ChatDTO;
import me.beiming.aichat.dto.ToolDefinitionDTO;
import me.beiming.aichat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai")
public class ChatController {
    @Autowired
    private ChatService chatService;

    @Operation(summary = "获取可用工具列表",
            description = "返回所有已注册的工具定义（含内置@ToolDef和MCP远程工具），供前端展示工具选择界面。" +
                    "providerType: BUILTIN=内置, MCP=MCP远程。")
    @PostMapping("/tools")
    public List<ToolDefinitionDTO> getTools() {
        return chatService.getToolDefinitions();
    }

    @Operation(summary = "聊天（支持ToolCalling）",
            description = "统一聊天接口，支持流式输出（SSE）和工具调用。根据会话toolPreferences自动启用/禁用工具。" +
                    "SSE事件类型: content(文本), reasoning(思考), tool_start(工具开始), tool_end(工具结束), error(错误)。")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatDTO dto) {
        log.debug("[Controller] /ai/chat 被调用: conversationId={}, enableTools={}, toolNames={}, messageLen={}",
                dto.getConversationId(), dto.getEnableTools(), dto.getToolNames(),
                dto.getMessage() != null ? dto.getMessage().length() : 0);
        return chatService.chat(dto);
    }

    @Operation(summary = "深度思考聊天",
            description = "深度思考聊天接口，AI输出思考过程。支持流式输出（SSE）和工具调用。")
    @PostMapping(value = "/chat/deep-thinking", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> deepThinkingChat(@RequestBody ChatDTO dto) {
        log.debug("[Controller] /ai/chat/deep-thinking 被调用: conversationId={}, enableTools={}, toolNames={}",
                dto.getConversationId(), dto.getEnableTools(), dto.getToolNames());
        return chatService.deepThinkingChat(dto);
    }
}
