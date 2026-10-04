package me.beiming.aichat.entity.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import me.beiming.aichat.constant.RoleConstant;

import java.util.List;

/**
 * 助手消息，角色固定为 ASSISTANT，负责承载 AI 回复与工具调用请求。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AssistantMessage extends Message {

    public AssistantMessage(String content) {
        super(RoleConstant.ASSISTANT, content);
    }

    public AssistantMessage(String content, int depth) {
        super(RoleConstant.ASSISTANT, content);
        this.setDepth(depth);
    }

    /**
     * 创建携带工具调用请求的 ASSISTANT 消息（content 为 null）。
     */
    public static AssistantMessage withToolCalls(List<Message.ToolCall> toolCalls, int depth) {
        AssistantMessage msg = new AssistantMessage(null, depth);
        msg.setToolCalls(toolCalls);
        return msg;
    }
}
