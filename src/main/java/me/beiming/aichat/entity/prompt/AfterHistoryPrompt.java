package me.beiming.aichat.entity.prompt;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.beiming.aichat.entity.message.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史后提示词，紧跟在历史提示词之后发送给 AI。
 * 内容来源于会话配置的 after_history_prompt 字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AfterHistoryPrompt extends Prompt {

    /**
     * 由历史后提示词文本构建，内容为空白时消息列表为空。
     */
    public static AfterHistoryPrompt of(String afterHistoryPrompt) {
        AfterHistoryPrompt p = new AfterHistoryPrompt();
        p.setMessages(afterHistoryPrompt == null || afterHistoryPrompt.isBlank()
                ? new ArrayList<>()
                : List.of(new SystemMessage(afterHistoryPrompt)));
        return p;
    }
}
