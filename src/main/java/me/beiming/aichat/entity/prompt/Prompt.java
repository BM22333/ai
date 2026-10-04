package me.beiming.aichat.entity.prompt;

import lombok.Data;
import me.beiming.aichat.entity.message.Message;

import java.util.List;

/**
 * 提示词基类，持有该部分提示词对应的消息列表。
 * 四个子类共同决定最终发给 AI 的内容：
 * SystemPrompt（最前）→ HistoryPrompt（居中，含 InsertPrompt 按 depth 注入）→ AfterHistoryPrompt（紧跟其后）。
 */
@Data
public class Prompt {
    private List<Message> messages;
}
