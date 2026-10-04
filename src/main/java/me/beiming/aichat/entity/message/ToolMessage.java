package me.beiming.aichat.entity.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import me.beiming.aichat.constant.RoleConstant;

/**
 * 工具结果消息，角色固定为 TOOL，负责承载工具调用结果。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ToolMessage extends Message {

    public ToolMessage(String toolCallId, String toolName, String content, int depth) {
        super(RoleConstant.TOOL, content);
        this.setToolCallId(toolCallId);
        this.setToolName(toolName);
        this.setDepth(depth);
    }

    /**
     * 创建一条工具调用结果（TOOL 角色）消息。
     */
    public static ToolMessage toolResult(String toolCallId, String toolName, String result, int depth) {
        return new ToolMessage(toolCallId, toolName, result, depth);
    }
}
