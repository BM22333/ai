package me.beiming.aichat.entity.prompt;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.beiming.aichat.entity.message.Message;

import java.util.List;

/**
 * 历史对话提示词，位于最终消息的中间。
 * 传入的列表应为已过滤（isSeeable）、清理孤儿、按最大消息数截断后的历史。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HistoryPrompt extends Prompt {

    public static HistoryPrompt of(List<Message> history) {
        HistoryPrompt p = new HistoryPrompt();
        p.setMessages(history);
        return p;
    }
}
