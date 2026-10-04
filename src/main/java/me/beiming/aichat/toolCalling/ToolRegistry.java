package me.beiming.aichat.toolCalling;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.beiming.aichat.entity.ToolDefinition;
import me.beiming.aichat.annotation.ToolDef;
import me.beiming.aichat.annotation.ToolParamDef;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 工具注册中心。
 * <p>
 * 启动时扫描所有带 {@link ToolDef} 注解的 Spring Bean 方法，
 * 注册到线程安全的 Map 中。提供工具查找、OpenAI 格式转换和反射执行能力。
 * </p>
 */
@Slf4j
@Service
public class ToolRegistry implements ApplicationContextAware {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ApplicationContext applicationContext;

    /**
     * AI 安全名 → 原始工具名的反向映射。
     * <p>
     * AI API 要求函数名匹配 {@code ^[a-zA-Z0-9_-]+$}，但 MCP 工具名可能包含中文等字符。
     * 注册工具时自动生成安全名（替换非法字符），AI 返回时再映射回原始名执行。
     * </p>
     */
    private final Map<String, String> aiNameToOriginal = new ConcurrentHashMap<>();

    /** AI 函数名允许的字符模式（补集用于替换） */
    private static final Pattern AI_NAME_INVALID = Pattern.compile("[^a-zA-Z0-9_\\-]");

    @Lazy  // 打破 McpServerManager ↔ ToolRegistry 循环依赖
    @Autowired
    private McpServerManager mcpServerManager;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        scanTools();
    }

    /**
     * 扫描所有 Bean 中带 @ToolDef 注解的方法，注册为可用工具。
     */
    private void scanTools() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                // 跳过 Spring 内部 Bean 和代理类，避免重复扫描
                Method[] methods = bean.getClass().getDeclaredMethods();
                for (Method method : methods) {
                    ToolDef annotation = method.getAnnotation(ToolDef.class);
                    if (annotation != null) {
                        ToolDefinition def = buildToolDefinition(bean, method, annotation);
                        generateAiSafeName(def);
                        tools.put(annotation.name(), def);
                        log.info("注册工具: {} [{}]", annotation.name(), beanName);
                    }
                }
            } catch (Exception e) {
                // 某些 Bean 可能无法提前获取（如 lazy-init），跳过即可
                log.trace("跳过 Bean [{}]: {}", beanName, e.getMessage());
            }
        }
        log.info("工具注册完成，共 {} 个工具", tools.size());
    }

    /**
     * 根据注解信息构建 ToolDefinition。
     */
    private ToolDefinition buildToolDefinition(Object bean, Method method, ToolDef annotation) {
        ToolDefinition def = new ToolDefinition();
        def.setName(annotation.name());
        def.setDescription(annotation.description());
        def.setProviderType("BUILTIN");
        def.setBean(bean);
        def.setMethod(method);
        def.setParametersSchema(buildParametersSchema(method));
        return def;
    }

    /**
     * 从方法参数的 @ToolParamDef 注解构建 JSON Schema。
     */
    private Map<String, Object> buildParametersSchema(Method method) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            ToolParamDef paramDef = param.getAnnotation(ToolParamDef.class);
            if (paramDef != null) {
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", paramDef.type());
                prop.put("description", paramDef.description());

                // 枚举值支持
                if (paramDef.enumValues().length > 0) {
                    prop.put("enum", Arrays.asList(paramDef.enumValues()));
                }

                properties.put(paramDef.name(), prop);

                if (paramDef.required()) {
                    required.add(paramDef.name());
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /**
     * 按名称查找工具定义。
     */
    public ToolDefinition getTool(String name) {
        return tools.get(name);
    }

    /**
     * 获取所有已注册工具定义。
     */
    public Collection<ToolDefinition> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * 获取所有已注册工具名称（含 BUILTIN 和 MCP）。
     * <p>
     * 用于工具发现/展示场景（如前端工具列表）。
     * 注意：MCP 工具默认不暴露给 AI，仅当用户在会话或请求中显式指定时才生效。
     * </p>
     */
    public List<String> getAllToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    /**
     * 获取仅 BUILTIN（本地）工具名称。
     * <p>
     * 当 {@code enableTools=true} 时使用，确保 MCP 工具不会自动全部暴露给 AI。
     * </p>
     */
    public List<String> getBuiltinToolNames() {
        List<String> names = new ArrayList<>();
        for (var entry : tools.entrySet()) {
            if (!"MCP".equals(entry.getValue().getProviderType())) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    /**
     * 获取仅 MCP（远程）工具名称。
     * <p>
     * 用于前端按 MCP Server 分组展示，方便用户选择性启用。
     * </p>
     */
    public List<String> getMcpToolNames() {
        List<String> names = new ArrayList<>();
        for (var entry : tools.entrySet()) {
            if ("MCP".equals(entry.getValue().getProviderType())) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    /**
     * 注册一个 MCP 远程工具到全局注册表。
     * <p>
     * 命名格式: {@code mcp_{userId}_{serverName}_{toolName}}，使用单下划线连接。
     * 与 BUILTIN 工具共享同一个 {@link ConcurrentHashMap}，通过 {@code providerType="MCP"} 区分。
     * </p>
     *
     * @param def 工具定义（name 必须符合 MCP 命名规范）
     */
    public void registerMcpTool(ToolDefinition def) {
        generateAiSafeName(def);
        tools.put(def.getName(), def);
        log.info("注册 MCP 工具: {} [{}], aiSafeName={}", def.getName(), def.getProviderType(), def.getAiSafeName());
    }

    /**
     * 为工具定义生成 AI API 兼容的安全名称。
     * <p>
     * 替换所有不在 {@code [a-zA-Z0-9_-]} 范围内的字符为下划线，
     * 若产生冲突则追加数字后缀。
     * </p>
     */
    private void generateAiSafeName(ToolDefinition def) {
        String originalName = def.getName();
        String safe = AI_NAME_INVALID.matcher(originalName).replaceAll("_");

        // 去重：已有同名安全名 → 追加数字后缀
        String candidate = safe;
        int suffix = 0;
        while (aiNameToOriginal.containsKey(candidate)) {
            // 如果已映射到同一个原始名，无需追加后缀
            if (originalName.equals(aiNameToOriginal.get(candidate))) {
                break;
            }
            suffix++;
            candidate = safe + "_" + suffix;
        }

        def.setAiSafeName(candidate);
        aiNameToOriginal.put(candidate, originalName);
    }

    /**
     * 按完整前缀批量注销某个 MCP Server 下的所有工具。
     * <p>
     * 前缀格式: {@code mcp_{userId}_{serverName}_}，由 {@link McpServerManager#disconnect} 调用。
     * </p>
     *
     * @param prefix 工具名前缀，如 "mcp_uuid_weather_"
     */
    public void unregisterMcpToolsByPrefix(String prefix) {
        Iterator<String> it = tools.keySet().iterator();
        int count = 0;
        while (it.hasNext()) {
            String name = it.next();
            if (name.startsWith(prefix)) {
                ToolDefinition def = tools.get(name);
                if (def != null && def.getAiSafeName() != null) {
                    aiNameToOriginal.remove(def.getAiSafeName());
                }
                it.remove();
                count++;
            }
        }
        if (count > 0) {
            log.info("注销 {} 个 MCP 工具 (prefix={})", count, prefix);
        }
    }

    /**
     * 注销某个用户的所有 MCP 工具。
     * <p>
     * 匹配所有以 {@code mcp_{userId}_} 开头的工具名。
     * </p>
     *
     * @param userId 用户 ID
     */
    public void unregisterMcpToolsByUser(String userId) {
        String prefix = "mcp_" + userId + "_";
        Iterator<String> it = tools.keySet().iterator();
        int count = 0;
        while (it.hasNext()) {
            String name = it.next();
            if (name.startsWith(prefix)) {
                ToolDefinition def = tools.get(name);
                if (def != null && def.getAiSafeName() != null) {
                    aiNameToOriginal.remove(def.getAiSafeName());
                }
                it.remove();
                count++;
            }
        }
        if (count > 0) {
            log.info("注销用户 {} 的 {} 个 MCP 工具", userId, count);
        }
    }

    /**
     * 将指定名称的工具定义转为 OpenAI Function Calling 格式。
     */
    public List<Map<String, Object>> toOpenAiFormat(Collection<String> toolNames) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : toolNames) {
            ToolDefinition def = tools.get(name);
            if (def != null) {
                result.add(def.toOpenAiFormat());
            } else {
                log.warn("请求了未注册的工具: {}", name);
            }
        }
        return result;
    }

    /**
     * 执行指定工具。
     *
     * @param toolName       工具名称
     * @param argumentsJson  JSON 参数字符串，如 {"expression": "2+2"}
     * @return 工具执行结果字符串
     */
    public String execute(String toolName, String argumentsJson) {
        // 将 AI 安全名映射回原始工具名（AI API 只接受安全名，内部使用原始名）
        String resolvedName = aiNameToOriginal.getOrDefault(toolName, toolName);
        ToolDefinition def = tools.get(resolvedName);
        if (def == null) {
            return "错误: 未找到工具 " + toolName;
        }

        // MCP 远程工具 → 委托给 McpServerManager 执行
        // 注意：必须传 resolvedName（原始名），不能传 toolName（AI安全名）
        // 因为 McpServerManager.toolMetaMap 的 key 使用原始 serverName
        if ("MCP".equals(def.getProviderType())) {
            return mcpServerManager.execute(resolvedName, argumentsJson);
        }

        // BUILTIN 本地工具 → 反射执行
        try {
            // 解析 JSON 参数
            @SuppressWarnings("unchecked")
            Map<String, Object> argsMap = objectMapper.readValue(argumentsJson, Map.class);

            // 按方法参数顺序映射
            Parameter[] params = def.getMethod().getParameters();
            Object[] invokeArgs = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                ToolParamDef paramDef = params[i].getAnnotation(ToolParamDef.class);
                if (paramDef != null) {
                    Object value = argsMap.get(paramDef.name());
                    // 类型转换
                    invokeArgs[i] = convertArg(value, params[i].getType());
                }
            }

            Object result = def.getMethod().invoke(def.getBean(), invokeArgs);
            return result != null ? result.toString() : "";

        } catch (Exception e) {
            log.error("工具执行失败: tool={}, args={}", toolName, argumentsJson, e);
            return "工具执行错误: " + e.getMessage();
        }
    }

    /**
     * 将 JSON 解析值转为目标 Java 类型。
     */
    private Object convertArg(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        if (targetType == Integer.class || targetType == int.class) {
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
        }
        if (targetType == Double.class || targetType == double.class) {
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
        }
        if (targetType == Long.class || targetType == long.class) {
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
        }

        return value.toString();
    }
}
