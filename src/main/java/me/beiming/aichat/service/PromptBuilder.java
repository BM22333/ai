package me.beiming.aichat.service;

import me.beiming.aichat.constant.ChatConstant;
import me.beiming.aichat.constant.RoleConstant;
import me.beiming.aichat.entity.Conversation;
import me.beiming.aichat.entity.message.Message;
import me.beiming.aichat.entity.prompt.AfterHistoryPrompt;
import me.beiming.aichat.entity.prompt.InsertPrompt;
import me.beiming.aichat.entity.prompt.SystemPrompt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 提示词构建器：将系统提示词 / 历史对话提示词 / 历史后提示词 / 注入提示词
 * 组装为最终发给 AI 的 OpenAI 兼容消息列表。
 *
 * <p>最终结构：系统提示词（最前）→ 历史提示词（居中，注入提示词按 depth 插入到同 depth 消息之前）
 * → 历史后提示词（紧跟其后）。</p>
 */
public final class PromptBuilder {

    private PromptBuilder() {
    }

    /**
     * 唯一入口：组装最终发给 AI 的消息列表。
     *
     * @param conversation   会话配置（systemPrompt / afterHistoryPrompt / maxMessages）
     * @param history        完整历史（含本次新追加的用户消息）
     * @param insertPrompts  本次请求要注入的提示词列表，可为 null/空
     * @return OpenAI 兼容的 messages 列表
     */
    public static List<Map<String, Object>> buildOpenAiMessages(Conversation conversation,
                                                                List<Message> history,
                                                                List<InsertPrompt> insertPrompts) {
        int maxMessages = (conversation.getMaxMessages() != null)
                ? conversation.getMaxMessages() : ChatConstant.DEFAULT_MAX_MESSAGES;

        // 1. 系统提示词（最前）
        List<Message> combined = new ArrayList<>(SystemPrompt.of(conversation.getSystemPrompt()).getMessages());

        // 2. 历史提示词（居中）：过滤可见 → 清理孤儿 → 截断 → 再清理孤儿 → 按 depth 注入
        List<Message> historyMessages = buildHistoryMessages(history, maxMessages);
        historyMessages = injectInsertPrompts(historyMessages, insertPrompts);
        combined.addAll(historyMessages);

        // 3. 历史后提示词（紧跟历史之后）
        combined.addAll(AfterHistoryPrompt.of(conversation.getAfterHistoryPrompt()).getMessages());

        return toOpenAiMessages(combined);
    }

    /**
     * 从完整历史中构建发给 AI 的历史消息列表，三步走（顺序重要）：
     * <ol>
     *   <li>过滤对 AI 不可见（isSeeable=false）的消息；</li>
     *   <li>孤儿清理（不动点迭代）：TOOL 消息其前一条保留消息不是「携带 tool_calls 的 ASSISTANT」则移除，
     *       反之携带 tool_calls 的 ASSISTANT 其后一条保留消息不是 TOOL 则移除（AI API 要求消息链配对完整）；</li>
     *   <li>截断取最后 maxMessages 条：窗口首条是 TOOL 则前移丢弃（其配对被截掉）、
     *       窗口末条是携带 tool_calls 的 ASSISTANT 则后移丢弃（其工具结果被截掉），截断后再跑一次孤儿清理。</li>
     * </ol>
     *
     * @param history     完整历史
     * @param maxMessages 展示给 AI 的最大消息数
     * @return 处理后仍按历史顺序的消息列表
     */
    public static List<Message> buildHistoryMessages(List<Message> history, int maxMessages) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 过滤可见（null 视为可见，兼容旧数据）
        List<Message> visible = new ArrayList<>();
        for (Message msg : history) {
            if (msg.getIsSeeable() == null || msg.getIsSeeable()) {
                visible.add(msg);
            }
        }

        // 2. 孤儿清理（不动点迭代）
        List<Message> cleaned = cleanupOrphanMessages(visible);

        // 3. 截断
        cleaned = truncateToMax(cleaned, maxMessages);

        // 4. 截断可能制造新孤儿，再清理一次
        return cleanupOrphanMessages(cleaned);
    }

    /**
     * 将注入提示词按 depth 插入历史消息中间：
     * 某 depth 组的提示词插入到「历史中第一条 depth 相同消息」之前；
     * 历史中无匹配 depth 的组追加到历史末尾。
     * 同一 depth 的多条提示词按传入顺序注入。
     */
    public static List<Message> injectInsertPrompts(List<Message> historyMessages,
                                                    List<InsertPrompt> insertPrompts) {
        if (insertPrompts == null || insertPrompts.isEmpty()) {
            return historyMessages;
        }

        // 按 depth 分组（LinkedHashMap 保持传入顺序）
        Map<Integer, List<InsertPrompt>> groups = new LinkedHashMap<>();
        for (InsertPrompt p : insertPrompts) {
            groups.computeIfAbsent(p.getDepth(), k -> new ArrayList<>()).add(p);
        }

        Set<Integer> consumedDepths = new HashSet<>();
        List<Message> result = new ArrayList<>(historyMessages.size());

        for (int i = 0; i < historyMessages.size(); i++) {
            Message msg = historyMessages.get(i);

            // 判断是否为该 depth 段的第一条消息
            boolean firstOfDepth = (i == 0)
                    || historyMessages.get(i - 1).getDepth() != msg.getDepth();
            if (firstOfDepth) {
                List<InsertPrompt> group = groups.get(msg.getDepth());
                if (group != null) {
                    consumedDepths.add(msg.getDepth());
                    for (InsertPrompt p : group) {
                        result.addAll(p.getMessages());
                    }
                }
            }

            result.add(msg);
        }

        // 历史中无匹配 depth 的组追加到末尾
        for (Map.Entry<Integer, List<InsertPrompt>> entry : groups.entrySet()) {
            if (!consumedDepths.contains(entry.getKey())) {
                for (InsertPrompt p : entry.getValue()) {
                    result.addAll(p.getMessages());
                }
            }
        }
        return result;
    }

    /**
     * 将 Message 列表转换为 OpenAI 兼容格式（USER/ASSISTANT/TOOL/SYSTEM 分支），其他角色跳过。
     */
    public static List<Map<String, Object>> toOpenAiMessages(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message msg : messages) {
            String role = msg.getRole();

            if (RoleConstant.SYSTEM.equals(role)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", "system");
                item.put("content", msg.getContent() != null ? msg.getContent() : "");
                result.add(item);

            } else if (RoleConstant.USER.equals(role)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", "user");
                item.put("content", msg.getContent() != null ? msg.getContent() : "");
                result.add(item);

            } else if (RoleConstant.ASSISTANT.equals(role)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", "assistant");

                if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    // AI 请求工具调用的 ASSISTANT 消息
                    item.put("content", msg.getContent());
                    item.put("tool_calls", buildToolCallsArray(msg.getToolCalls()));
                } else {
                    // 普通文本回复
                    item.put("content", msg.getContent() != null ? msg.getContent() : "");
                }
                result.add(item);

            } else if (RoleConstant.TOOL.equals(role)) {
                // 工具调用结果消息
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", "tool");
                item.put("tool_call_id", msg.getToolCallId());
                item.put("name", msg.getToolName());
                item.put("content", msg.getContent() != null ? msg.getContent() : "");
                result.add(item);
            }
            // 其他角色跳过
        }
        return result;
    }

    /**
     * 孤儿消息清理（不动点迭代）：
     * <ul>
     *   <li>TOOL 消息，其前一条保留消息不是「携带 tool_calls 的 ASSISTANT」→ 移除；</li>
     *   <li>携带 tool_calls 的 ASSISTANT，其后一条保留消息不是 TOOL → 移除。</li>
     * </ul>
     * 循环直到无变化，保证 assistant(tc) ↔ tool 消息链配对完整。
     */
    private static List<Message> cleanupOrphanMessages(List<Message> list) {
        List<Message> current = new ArrayList<>(list);
        boolean changed;
        do {
            changed = false;
            List<Message> next = new ArrayList<>(current.size());
            for (int i = 0; i < current.size(); i++) {
                Message msg = current.get(i);
                String role = msg.getRole();

                // TOOL 消息必须紧跟在携带 tool_calls 的 ASSISTANT 之后
                if (RoleConstant.TOOL.equals(role)) {
                    Message prev = next.isEmpty() ? null : next.get(next.size() - 1);
                    if (!isToolCallRequest(prev)) {
                        changed = true;
                        continue;
                    }
                }

                // 携带 tool_calls 的 ASSISTANT 必须后随 TOOL 消息
                if (isToolCallRequest(msg)) {
                    Message after = (i + 1 < current.size()) ? current.get(i + 1) : null;
                    if (after == null || !RoleConstant.TOOL.equals(after.getRole())) {
                        changed = true;
                        continue;
                    }
                }

                next.add(msg);
            }
            current = next;
        } while (changed);
        return current;
    }

    /**
     * 截断保留最后 maxMessages 条，双侧处理消息链边界：
     * 窗口首条是 TOOL（配对 assistant(tc) 被截掉）→ 前移丢弃；
     * 窗口末条是携带 tool_calls 的 ASSISTANT（工具结果被截掉）→ 后移丢弃。
     */
    private static List<Message> truncateToMax(List<Message> list, int maxMessages) {
        if (list.size() <= maxMessages) {
            return list;
        }

        int start = list.size() - maxMessages;

        // 首条是 TOOL：其配对的 assistant(tc) 已被截掉，丢弃
        while (start < list.size() && RoleConstant.TOOL.equals(list.get(start).getRole())) {
            start++;
        }

        // 末条是携带 tool_calls 的 ASSISTANT：其工具结果已被截掉，丢弃
        int end = list.size();
        while (end > start && isToolCallRequest(list.get(end - 1))) {
            end--;
        }

        return new ArrayList<>(list.subList(start, end));
    }

    /**
     * 判断消息是否为携带非空 tool_calls 的 ASSISTANT 消息。
     */
    private static boolean isToolCallRequest(Message msg) {
        return msg != null && RoleConstant.ASSISTANT.equals(msg.getRole())
                && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty();
    }

    /**
     * 将 Message.ToolCall 列表转为 OpenAI tool_calls 格式。
     */
    private static List<Map<String, Object>> buildToolCallsArray(List<Message.ToolCall> toolCalls) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message.ToolCall tc : toolCalls) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("id", tc.getId());
            call.put("type", tc.getType() != null ? tc.getType() : "function");

            Map<String, String> fn = new LinkedHashMap<>();
            fn.put("name", tc.getFunctionName());
            fn.put("arguments", tc.getArguments());
            call.put("function", fn);

            result.add(call);
        }
        return result;
    }
}
