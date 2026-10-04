package me.beiming.aichat.service;

import lombok.extern.slf4j.Slf4j;
import me.beiming.aichat.constant.ChatConstant;
import me.beiming.aichat.constant.MessageConstant;
import me.beiming.aichat.constant.RedisConstant;
import me.beiming.aichat.dto.UpdateConversationDTO;
import me.beiming.aichat.entity.Conversation;
import me.beiming.aichat.entity.message.Message;
import me.beiming.aichat.entity.Result;
import me.beiming.aichat.entity.user.UserConversation;
import me.beiming.aichat.mapper.ContextMemoryMapper;
import me.beiming.aichat.mapper.ConversationMapper;
import me.beiming.aichat.mapper.UserConversationMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ConversationService {
    @Autowired
    private ConversationMapper conversationMapper;
    @Autowired
    private ContextMemoryMapper contextMemoryMapper;
    @Autowired
    private UserConversationMapper userConversationMapper;
    @Autowired
    private RedisTemplate<String, Object> template;
    @Autowired
    private JdbcChatMemoryRepository chatMemoryRepository;

    /**
     * 创建会话
     * <p>支持部分为null（userId和apiKey除外）</p>
     *
     * @param conversationName 会话名称
     * @param systemPrompt 系统提示词
     * @param apiKey 秘钥
     * @param model 指定模型
     * @param baseUrl 调用模型的base_url
     * @param userId 用户的Id
     * @param maxMessages 展示给AI的最大消息数，<=0 时使用默认值
     * @param maxToolCallDepth 最大工具调用深度，<=0 时使用默认值
     * @param afterHistoryPrompt 历史后提示词
     * @param temperature AI 温度参数，null 时使用默认值 1.0
     * @param reasoningEffort 推理力度，null/空时使用默认值 high
     * @return 返回生成的会话Id
     */
    public Result<String> createConversation(String conversationName, String systemPrompt, String apiKey, String model, String baseUrl, String userId, int maxMessages, int maxToolCallDepth, String afterHistoryPrompt, Double temperature, String reasoningEffort) {

        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException(MessageConstant.USER_ID_EMPTY_ERROR);
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException(MessageConstant.API_KEY_EMPTY_ERROR);
        }

        if (conversationName == null || conversationName.trim().isEmpty()) {
            conversationName = "未命名会话";
        }

        // 非法值回退默认
        if (maxMessages <= 0) {
            maxMessages = ChatConstant.DEFAULT_MAX_MESSAGES;
        }
        if (maxToolCallDepth <= 0) {
            maxToolCallDepth = ChatConstant.DEFAULT_MAX_TOOL_CALL_DEPTH;
        }

        // 生成conversationId
        String conversationId = UUID.randomUUID().toString();

        Conversation conversation = new Conversation(conversationId, conversationName, model, systemPrompt, apiKey, baseUrl);
        conversation.setMaxMessages(maxMessages);
        conversation.setMaxToolCallDepth(maxToolCallDepth);
        conversation.setAfterHistoryPrompt(afterHistoryPrompt);
        conversation.setTemperature(temperature != null ? temperature : ChatConstant.DEFAULT_TEMPERATURE);
        conversation.setReasoningEffort(reasoningEffort != null && !reasoningEffort.trim().isEmpty()
                ? reasoningEffort : ChatConstant.DEFAULT_REASONING_EFFORT);

        // 保存到数据库
        conversationMapper.insert(conversation);

        // 将用户和会话关联起来
        UserConversation relationship = new UserConversation(conversationId, userId);
        userConversationMapper.insert(relationship);

        return Result.success(conversationId);
    }

    /**
     * 连接会话
     *<p><b>行为</b>：根据会话Id从数据库中查询该会话以及用户会话关系表，确保该会话存在。若会话不存在或者不在用户会话关系表当中，均返回错误结果</p>
     *
     * @param conversationId 会话Id
     * @return 成功信息
     */
    public Result connectConversation(String conversationId) {

        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new IllegalArgumentException(MessageConstant.CONVERSATION_NOT_FOUND_ERROR);
        }

        //从数据库中查询Conversation
        Conversation conversation = conversationMapper.selectByConversationId(conversationId);

        // 从数据库中查询UserConversation关系
        UserConversation relationship = userConversationMapper.selectByConversationId(conversationId);

        if (conversation == null || relationship == null) {
            throw new IllegalArgumentException(MessageConstant.CONVERSATION_NOT_FOUND_ERROR);
        }

        return Result.success();
    }

    /**
     * 获取历史对话
     * <p><b>行为</b>：从Redis当中获取历史对话</p>
     *
     * @param conversationId 会话Id
     * @return 历史对话
     */
    public Result<List<Message>> getChatHistory(String conversationId) {

        String redisKey = RedisConstant.CHAT_HISTORY + conversationId;
        List<Message> history = (List<Message>) template.opsForValue().get(redisKey);

        return Result.success(history);
    }

    /**
     * 删除会话
     * <p><b>行为</b>：删除会话表、用户会话关系表以及Redis中的历史对话</p>
     *
     * @param conversationId 会话Id
     * @return 成功消息
     */
    public Result<String> deleteConversation(String conversationId) {
        // 删除会话表中的数据
        conversationMapper.deleteByConversationId(conversationId);

        // 删除用户会话关系表中的数据
        userConversationMapper.deleteByConversationId(conversationId);

        // 删除Redis中的历史对话
        String redisKey = RedisConstant.CHAT_HISTORY + conversationId;
        template.delete(redisKey);
        return Result.success(MessageConstant.CONVERSATION_DELETED_SUCCESSY);
    }

    /**
     * 获取用户的会话表。
     *
     * @param userId 用户Id
     * @return 会话列表
     */
    public Result<List<Conversation>> getUserConversations(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Result.error("用户ID不能为空");
        }

        // 查询用户的所有会话关系
        List<UserConversation> userConversations = userConversationMapper.selectByUserId(userId);
        
        if (userConversations == null || userConversations.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        // 获取所有会话ID
        List<String> conversationIds = userConversations.stream()
                .map(UserConversation::getConversationId)
                .toList();

        // 查询会话详情
        List<Conversation> conversationList = new ArrayList<>();
        for (String conversationId : conversationIds) {
            Conversation conversation = conversationMapper.selectByConversationId(conversationId);
            if (conversation != null) {
                conversationList.add(conversation);
            }
        }

        return Result.success(conversationList);
    }

    /**
     * 回溯会话到指定的时间戳。
     * <p><b>行为</b>：指定时间戳之后的内容全部删除</p>
     *
     * @param conversationId 会话Id
     * @param timestamp 时间戳
     * @return  回溯成功 + 删除消息数
     */
    public Result<String> rollbackConversation(String conversationId, Long timestamp) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return Result.error("会话ID不能为空");
        }

        if (timestamp == null) {
            return Result.error("回溯时间戳不能为空");
        }

        String redisKey = RedisConstant.CHAT_HISTORY + conversationId;
        List<Message> fullHistory = (List<Message>) template.opsForValue().get(redisKey);

        if (fullHistory == null || fullHistory.isEmpty()) {
            return Result.error("会话历史记录不存在");
        }

        int rollbackIndex = -1;
        for (int i = 0; i < fullHistory.size(); i++) {
            Message message = fullHistory.get(i);
            if (message.getTimestamp() != null && message.getTimestamp().getTime() > timestamp) {
                rollbackIndex = i;
                break;
            }
        }

        if (rollbackIndex == -1) {
            return Result.error("指定的回溯点不存在");
        }

        List<Message> rolledBackMessages;
        if (rollbackIndex == 0) {
            rolledBackMessages = new ArrayList<>();
        } else {
            rolledBackMessages = new ArrayList<>(fullHistory.subList(0, rollbackIndex));
        }
        template.opsForValue().set(redisKey, rolledBackMessages);

        contextMemoryMapper.deleteByConversationId(conversationId);

        rebuildChatMemory(conversationId, rolledBackMessages);

        int deletedCount = fullHistory.size() - rolledBackMessages.size();
        return Result.success("回溯成功，删除了" + deletedCount + "条消息");
    }

    /**
     * 更新会话配置。
     * <p>支持部分更新：仅更新DTO中非null的字段。</p>
     *
     * @param dto 更新参数，conversationId为必填
     * @return 更新成功消息
     */
    public Result<String> updateConversation(UpdateConversationDTO dto) {
        if (dto.getConversationId() == null || dto.getConversationId().trim().isEmpty()) {
            return Result.error("会话ID不能为空");
        }

        // 查询会话是否存在
        Conversation conversation = conversationMapper.selectByConversationId(dto.getConversationId());
        if (conversation == null) {
            return Result.error(MessageConstant.CONVERSATION_NOT_FOUND_ERROR);
        }

        // 仅更新非null字段
        if (dto.getConversationName() != null) {
            conversation.setConversationName(dto.getConversationName());
        }
        if (dto.getSystemPrompt() != null) {
            conversation.setSystemPrompt(dto.getSystemPrompt());
        }
        if (dto.getModel() != null) {
            conversation.setModel(dto.getModel());
        }
        if (dto.getApiKey() != null) {
            conversation.setApiKey(dto.getApiKey());
        }
        if (dto.getBaseUrl() != null) {
            conversation.setBaseUrl(dto.getBaseUrl());
        }
        if (dto.getToolPreferences() != null) {
            conversation.setToolPreferences(dto.getToolPreferences());
            log.info("会话 {} 工具偏好已更新: {}", dto.getConversationId(), dto.getToolPreferences());
        }
        if (dto.getMaxMessages() != null) {
            conversation.setMaxMessages(dto.getMaxMessages() > 0 ? dto.getMaxMessages() : ChatConstant.DEFAULT_MAX_MESSAGES);
        }
        if (dto.getMaxToolCallDepth() != null) {
            conversation.setMaxToolCallDepth(dto.getMaxToolCallDepth() > 0 ? dto.getMaxToolCallDepth() : ChatConstant.DEFAULT_MAX_TOOL_CALL_DEPTH);
        }
        if (dto.getAfterHistoryPrompt() != null) {
            conversation.setAfterHistoryPrompt(dto.getAfterHistoryPrompt());
        }
        if (dto.getTemperature() != null) {
            conversation.setTemperature(dto.getTemperature() > 0 ? dto.getTemperature() : ChatConstant.DEFAULT_TEMPERATURE);
        }
        if (dto.getReasoningEffort() != null) {
            conversation.setReasoningEffort(!dto.getReasoningEffort().trim().isEmpty()
                    ? dto.getReasoningEffort() : ChatConstant.DEFAULT_REASONING_EFFORT);
        }

        // 保存到数据库
        conversationMapper.updateById(conversation);

        return Result.success("会话配置更新成功");
    }

    /**
     * 编辑指定索引的消息。
     * <p>
     * <b>行为：</b>更新消息内容后，截断该消息之后的所有后续消息。
     * 因为编辑会改变语义，后续 AI 响应基于旧内容已不再有效。
     * </p>
     * <p>
     * 完成后会重建 MySQL ChatMemory 并清除 ChatClient 缓存，
     * 确保下次对话使用修正后的历史上下文。
     * </p>
     *
     * @param conversationId 会话ID
     * @param messageIndex   消息索引（0-based）
     * @param newContent     新的消息内容
     * @return 编辑成功消息 + 清除消息数
     */
    public Result<String> editMessage(String conversationId, int messageIndex, String newContent) {
        // 1. 参数校验
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return Result.error("会话ID不能为空");
        }
        if (newContent == null || newContent.trim().isEmpty()) {
            return Result.error(MessageConstant.EDIT_CANNOT_BE_EMPTY);
        }

        // 2. 加载Redis中的历史记录
        String redisKey = RedisConstant.CHAT_HISTORY + conversationId;
        @SuppressWarnings("unchecked")
        List<Message> history = (List<Message>) template.opsForValue().get(redisKey);
        if (history == null || history.isEmpty()) {
            return Result.error(MessageConstant.HISTORY_EMPTY);
        }

        // 3. 校验索引
        if (messageIndex < 0 || messageIndex >= history.size()) {
            return Result.error(String.format(
                    MessageConstant.MESSAGE_INDEX_OUT_OF_BOUNDS,
                    history.size(), history.size() - 1));
        }

        // 4. 更新消息内容
        Message target = history.get(messageIndex);
        String oldRole = target.getRole();
        target.setContent(newContent.trim());
        target.setTimestamp(new Date()); // 更新编辑时间
        log.info("消息已编辑: conversationId={}, index={}, role={}, oldContent={}, newContent={}",
                conversationId, messageIndex, oldRole,
                target.getContent().substring(0, Math.min(50, target.getContent().length())),
                newContent.substring(0, Math.min(50, newContent.length())));

        // 5. 截断后续消息（从 index+1 开始全部删除）
        int removedCount = 0;
        if (history.size() > messageIndex + 1) {
            removedCount = history.size() - (messageIndex + 1);
            history = new ArrayList<>(history.subList(0, messageIndex + 1));
        }

        // 6. 保存回Redis
        template.opsForValue().set(redisKey, history);

        // 7. 重建MySQL ChatMemory（先清空，再导入截断后的消息）
        contextMemoryMapper.deleteByConversationId(conversationId);
        rebuildChatMemory(conversationId, history);

        return Result.success(String.format(
                MessageConstant.MESSAGE_EDIT_SUCCESS, removedCount));
    }

    /**
     * 删除指定索引的消息。
     * <p>
     * <b>行为：</b>仅删除该条消息，不截断后续消息。其他消息保持原索引不变。
     * （被删除消息之前的消息索引不变，之后的消息索引各前移 1 位。）
     * </p>
     * <p>
     * 完成后会重建 MySQL ChatMemory 并清除 ChatClient 缓存，
     * 确保下次对话使用删除后的历史上下文。
     * </p>
     *
     * @param conversationId 会话ID
     * @param messageIndex   消息索引（0-based），仅删除该条消息
     * @return 删除成功消息
     */
    public Result<String> deleteMessage(String conversationId, int messageIndex) {
        // 1. 参数校验
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return Result.error("会话ID不能为空");
        }

        // 2. 加载Redis中的历史记录
        String redisKey = RedisConstant.CHAT_HISTORY + conversationId;
        @SuppressWarnings("unchecked")
        List<Message> history = (List<Message>) template.opsForValue().get(redisKey);
        if (history == null || history.isEmpty()) {
            return Result.error(MessageConstant.HISTORY_EMPTY);
        }

        // 3. 校验索引
        if (messageIndex < 0 || messageIndex >= history.size()) {
            return Result.error(String.format(
                    MessageConstant.MESSAGE_INDEX_OUT_OF_BOUNDS,
                    history.size(), history.size() - 1));
        }

        // 4. 仅移除该条消息
        Message deletedMsg = history.remove(messageIndex);
        log.info("消息已删除: conversationId={}, index={}, role={}, content={}",
                conversationId, messageIndex, deletedMsg.getRole(),
                deletedMsg.getContent().substring(0, Math.min(50, deletedMsg.getContent().length())));

        // 5. 保存回Redis
        template.opsForValue().set(redisKey, history);

        // 6. 重建MySQL ChatMemory（先清空，再导入剩余消息）
        contextMemoryMapper.deleteByConversationId(conversationId);
        rebuildChatMemory(conversationId, history);

        return Result.success(MessageConstant.MESSAGE_DELETE_SUCCESS);
    }

    /**
     * 批量隐藏消息（设置 isSeeable=false）。
     * <p>隐藏后 AI 对话时不可见，但 getHistory 仍返回该消息（isSeeable=false 由前端渲染隐藏态）。</p>
     *
     * @param conversationId 会话ID
     * @param messageIndexes 消息索引列表（0-based），可重复，服务端去重
     * @return 隐藏成功消息 + 隐藏条数
     */
    public Result<String> hideMessages(String conversationId, List<Integer> messageIndexes) {
        return setMessagesVisibility(conversationId, messageIndexes, false, "隐藏");
    }

    /**
     * 批量取消隐藏消息（设置 isSeeable=true）。
     *
     * @param conversationId 会话ID
     * @param messageIndexes 消息索引列表（0-based），可重复，服务端去重
     * @return 恢复成功消息 + 恢复条数
     */
    public Result<String> showMessages(String conversationId, List<Integer> messageIndexes) {
        return setMessagesVisibility(conversationId, messageIndexes, true, "恢复");
    }

    /**
     * 隐藏/取消隐藏消息的公共实现。
     */
    private Result<String> setMessagesVisibility(String conversationId, List<Integer> messageIndexes,
                                                  boolean visible, String action) {
        // 1. 参数校验
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return Result.error("会话ID不能为空");
        }
        if (messageIndexes == null || messageIndexes.isEmpty()) {
            return Result.error("消息索引列表不能为空");
        }

        // 2. 加载Redis中的历史记录
        String redisKey = RedisConstant.CHAT_HISTORY + conversationId;
        @SuppressWarnings("unchecked")
        List<Message> history = (List<Message>) template.opsForValue().get(redisKey);
        if (history == null || history.isEmpty()) {
            return Result.error(MessageConstant.HISTORY_EMPTY);
        }

        // 3. 索引去重 + 校验（任一越界整体报错，避免部分成功）
        LinkedHashSet<Integer> uniqueIndexes = new LinkedHashSet<>(messageIndexes);
        for (Integer index : uniqueIndexes) {
            if (index == null || index < 0 || index >= history.size()) {
                return Result.error(String.format(
                        MessageConstant.MESSAGE_INDEX_OUT_OF_BOUNDS,
                        history.size(), history.size() - 1));
            }
        }

        // 4. 逐个设置可见性
        for (Integer index : uniqueIndexes) {
            Message target = history.get(index);
            target.setIsSeeable(visible);
            log.info("消息{}: conversationId={}, index={}, role={}",
                    action, conversationId, index, target.getRole());
        }

        // 5. 保存回Redis
        template.opsForValue().set(redisKey, history);

        return Result.success(String.format("已%s %d 条消息", action, uniqueIndexes.size()));
    }

    private void rebuildChatMemory(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // 按会话级配置的最大消息数截断，旧会话行该字段为 null 时回退默认值
        Conversation conversation = conversationMapper.selectByConversationId(conversationId);
        int maxMessages = (conversation != null && conversation.getMaxMessages() != null)
                ? conversation.getMaxMessages() : ChatConstant.DEFAULT_MAX_MESSAGES;

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMessages)
                .build();

        int startIndex = Math.max(0, messages.size() - maxMessages);
        List<Message> recentMessages = messages.subList(startIndex, messages.size());

        for (Message message : recentMessages) {
            org.springframework.ai.chat.messages.Message chatMessage;
            if ("USER".equals(message.getRole())) {
                chatMessage = new UserMessage(message.getContent());
            } else {
                chatMessage = new AssistantMessage(message.getContent());
            }
            chatMemory.add(conversationId, chatMessage);
        }
    }
}