package me.beiming.aichat.entity;

import lombok.Data;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已注册工具的内部模型，包含工具元数据和执行所需的方法引用。
 */
@Data
public class ToolDefinition {

    /** 工具唯一名称 */
    private String name;

    /**
     * AI API 兼容的函数名，仅包含 {@code [a-zA-Z0-9_-]}。
     * <p>
     * 原始 name 可能包含中文等字符（如 MCP Server 名称 "联网搜索"），
     * AI API 要求函数名匹配 {@code ^[a-zA-Z0-9_-]+$}，所以在注册时生成此安全名。
     * 在 {@link #toOpenAiFormat()} 中使用此名称，ToolRegistry 负责反向映射。
     * </p>
     */
    private String aiSafeName;

    /** 工具功能描述 */
    private String description;

    /** 来源: "BUILTIN" 或 "MCP" */
    private String providerType;

    /** OpenAI 兼容的 JSON Schema 参数定义 */
    private Map<String, Object> parametersSchema;

    /** Spring Bean 实例（用于反射调用） */
    private Object bean;

    /** 工具方法引用 */
    private Method method;

    /**
     * 构建 OpenAI Function Calling 兼容的工具定义 Map。
     * <pre>
     * {
     *   "type": "function",
     *   "function": {
     *     "name": "...",
     *     "description": "...",
     *     "parameters": { "type": "object", "properties": {...}, "required": [...] }
     *   }
     * }
     * </pre>
     */
    public Map<String, Object> toOpenAiFormat() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", aiSafeName != null ? aiSafeName : name);
        function.put("description", description);
        function.put("parameters", parametersSchema);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }
}
