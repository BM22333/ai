package me.beiming.aichat.controller;

import me.beiming.aichat.constant.MessageConstant;
import me.beiming.aichat.dto.ConversationIdDTO;
import me.beiming.aichat.dto.CreateConversationDTO;
import me.beiming.aichat.dto.DeleteMessageDTO;
import me.beiming.aichat.dto.EditMessageDTO;
import me.beiming.aichat.dto.MessageVisibilityDTO;
import me.beiming.aichat.dto.RollbackDTO;
import me.beiming.aichat.dto.UpdateConversationDTO;
import me.beiming.aichat.dto.UserIdDTO;
import me.beiming.aichat.entity.Conversation;
import me.beiming.aichat.entity.Result;
import me.beiming.aichat.entity.message.Message;
import me.beiming.aichat.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai/conversation")
@Tag(name = "会话管理", description = "AI对话会话的创建、连接、删除等管理接口")
public class ConversationController {
    @Autowired
    private ConversationService conversationService;

    @Operation(summary = "创建会话", description = "创建新的AI对话会话")
    @PostMapping("/create")
    public Result<String> create(@RequestBody CreateConversationDTO dto) {
        if (dto.getApiKey() == null || dto.getApiKey().isEmpty()) {
            return Result.error("apiKey不能为空");
        }
        if (dto.getUserId() == null || dto.getUserId().isEmpty()) {
            return Result.error(MessageConstant.USERNAME_OR_PASSWORD_EMPTY);
        }

        return conversationService.createConversation(dto.getConversationName(), dto.getSystemPrompt(), dto.getApiKey(), dto.getModel(), dto.getBaseUrl(), dto.getUserId(),
                dto.getMaxMessages() != null ? dto.getMaxMessages() : 0,
                dto.getMaxToolCallDepth() != null ? dto.getMaxToolCallDepth() : 0,
                dto.getAfterHistoryPrompt(),
                dto.getTemperature(),
                dto.getReasoningEffort());
    }

    @Operation(summary = "连接会话", description = "连接到已存在的会话")
    @PostMapping("/connect")
    public Result connect(@RequestBody ConversationIdDTO dto) {
        if (dto.getConversationId() == null || dto.getConversationId().isEmpty()) {
            return Result.error("conversationId不能为空");
        }

        return conversationService.connectConversation(dto.getConversationId());
    }

    @Operation(summary = "删除会话", description = "删除指定的会话")
    @PostMapping("/delete")
    public Result delete(@RequestBody ConversationIdDTO dto) {
        if (dto.getConversationId() == null || dto.getConversationId().isEmpty()) {
            return Result.error("conversationId不能为空");
        }

        return conversationService.deleteConversation(dto.getConversationId());
    }

    @Operation(summary = "获取历史记录", description = "获取指定会话的聊天历史")
    @PostMapping("/getHistory")
    public Result<List<Message>> getHistory(@RequestBody ConversationIdDTO dto) {
        if (dto.getConversationId() == null || dto.getConversationId().isEmpty()) {
            return Result.error("conversationId不能为空");
        }

        return conversationService.getChatHistory(dto.getConversationId());
    }

    @Operation(summary = "获取所有会话", description = "获取用户的所有会话列表")
    @PostMapping("/getAll")
    public Result<List<Conversation>> getAllConversations(@RequestBody UserIdDTO dto) {
        if (dto.getUserId() == null || dto.getUserId().isEmpty()) {
            return Result.error("用户ID不能为空");
        }

        return conversationService.getUserConversations(dto.getUserId());
    }

    @Operation(summary = "回溯对话", description = "将对话回溯到指定时间点")
    @PostMapping("/rollback")
    public Result<String> rollbackConversation(@RequestBody RollbackDTO dto) {
        if (dto.getConversationId() == null || dto.getConversationId().isEmpty()) {
            return Result.error("会话ID不能为空");
        }
        if (dto.getTimestamp() == null) {
            return Result.error("回溯时间戳不能为空");
        }

        return conversationService.rollbackConversation(dto.getConversationId(), dto.getTimestamp());
    }

    @Operation(summary = "更新会话配置",
            description = "部分更新会话配置（名称、系统提示词、模型、API Key、Base URL、工具偏好等），仅更新非null字段。" +
                    "toolPreferences格式为JSON数组字符串，如 [\"calculator\",\"date_time\"] 启用指定工具，[] 禁用所有，null不修改。")
    @PostMapping("/update")
    public Result<String> updateConversation(@RequestBody UpdateConversationDTO dto) {
        if (dto.getConversationId() == null || dto.getConversationId().isEmpty()) {
            return Result.error("会话ID不能为空");
        }
        return conversationService.updateConversation(dto);
    }

    @Operation(summary = "编辑消息",
            description = "编辑指定索引的消息内容，后续消息会被截断（因为编辑改变语义，后续 AI 响应已无效）。" +
                    "编辑后自动重建 MySQL ChatMemory 并清除 ChatClient 缓存。messageIndex 从 0 开始。")
    @PostMapping("/editMessage")
    public Result<String> editMessage(@RequestBody EditMessageDTO dto) {
        if (dto.getConversationId() == null || dto.getConversationId().isEmpty()) {
            return Result.error("会话ID不能为空");
        }
        if (dto.getNewContent() == null || dto.getNewContent().trim().isEmpty()) {
            return Result.error("编辑后的消息内容不能为空");
        }
        return conversationService.editMessage(
                dto.getConversationId(), dto.getMessageIndex(), dto.getNewContent());
    }

    @Operation(summary = "删除消息",
            description = "仅删除指定索引的单条消息，不影响其他消息。" +
                    "删除后自动重建 MySQL ChatMemory 并清除 ChatClient 缓存。messageIndex 从 0 开始。")
    @PostMapping("/deleteMessage")
    public Result<String> deleteMessage(@RequestBody DeleteMessageDTO dto) {
        if (dto.getConversationId() == null || dto.getConversationId().isEmpty()) {
            return Result.error("会话ID不能为空");
        }
        return conversationService.deleteMessage(dto.getConversationId(), dto.getMessageIndex());
    }

    @Operation(summary = "批量隐藏消息",
            description = "批量设置消息 isSeeable=false，隐藏后 AI 对话时不可见，" +
                    "但 getHistory 仍返回该消息（isSeeable=false 由前端渲染隐藏态）。messageIndexes 从 0 开始，可重复。")
    @PostMapping("/hideMessages")
    public Result<String> hideMessages(@RequestBody MessageVisibilityDTO dto) {
        if (dto.getConversationId() == null || dto.getConversationId().isEmpty()) {
            return Result.error("会话ID不能为空");
        }
        return conversationService.hideMessages(dto.getConversationId(), dto.getMessageIndexes());
    }

    @Operation(summary = "批量取消隐藏消息",
            description = "批量设置消息 isSeeable=true，恢复显示。messageIndexes 从 0 开始，可重复。")
    @PostMapping("/showMessages")
    public Result<String> showMessages(@RequestBody MessageVisibilityDTO dto) {
        if (dto.getConversationId() == null || dto.getConversationId().isEmpty()) {
            return Result.error("会话ID不能为空");
        }
        return conversationService.showMessages(dto.getConversationId(), dto.getMessageIndexes());
    }
}