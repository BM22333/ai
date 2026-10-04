package me.beiming.aichat.entity.prompt;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.beiming.aichat.entity.message.SystemMessage;

import java.util.List;

/**
 * 注入提示词，根据 depth 属性自动注入到历史提示词中间：
 * 插入到「历史中第一条 depth 相同消息」之前；历史中无匹配 depth 时追加到历史末尾。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InsertPrompt extends Prompt {

    /** 注入深度，对应历史消息的 depth 值 */
    private int depth;

    /**
     * 由提示词内容与注入深度构建，注入内容作为独立的 system 角色消息。
     * 注：若某些 AI 提供方拒绝消息链中间的 system 消息，可将注入方式改为合并进紧随其后的 user 消息。
     */
    public static InsertPrompt of(String content, int depth) {
        InsertPrompt p = new InsertPrompt();
        p.setDepth(depth);
        p.setMessages(List.of(new SystemMessage(content)));
        return p;
    }
}
