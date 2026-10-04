package me.beiming.aichat.entity.prompt;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.beiming.aichat.entity.message.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统提示词，最重要，位于最终消息的最前面。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SystemPrompt extends Prompt {

    /**
     * 由系统提示词文本构建，内容为空则消息列表为空。
     */
    public static SystemPrompt of(String systemPrompt) {
        SystemPrompt p = new SystemPrompt();
        p.setMessages(systemPrompt == null
                ? new ArrayList<>()
                : List.of(new SystemMessage(systemPrompt)));
        return p;
    }
}
