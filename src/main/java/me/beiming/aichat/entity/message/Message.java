package me.beiming.aichat.entity.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message implements Serializable {

    private String role;
    private String content;
    private Date timestamp;

    /** 消息深度，从 0 开始。用户消息=0；第 r 轮工具调用的 assistant(tc)+tool= r；最终 assistant 回复=最终轮次 */
    private int depth;

    /** AI 是否可见。null（旧数据）视为可见；新消息默认 true */
    private Boolean isSeeable;

    /** ASSISTANT 角色时非空 = AI 请求调用工具 */
    private List<ToolCall> toolCalls;

    /** TOOL 角色时非空 = 关联的 ToolCall.id */
    private String toolCallId;

    /** TOOL 角色时非空 = 函数名 */
    private String toolName;

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = new Date(System.currentTimeMillis());
        this.isSeeable = true;
    }

    /**
     * 工具调用内部类，表示 AI 请求调用的一次工具。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall implements Serializable {
        /** 工具调用唯一 ID，如 "call_abc123" */
        private String id;
        /** 固定 "function" */
        private String type;
        /** 函数名，如 "calculator" */
        private String functionName;
        /** JSON 参数字符串，如 {"expression":"2+2"} */
        private String arguments;
        /** 执行结果（仅在 TOOL 角色时填充） */
        private String result;
    }
}
