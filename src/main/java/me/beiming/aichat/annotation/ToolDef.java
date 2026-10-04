package me.beiming.aichat.annotation;

import me.beiming.aichat.toolCalling.ToolRegistry;

import java.lang.annotation.*;

/**
 * 标记一个方法为 AI 可调用的工具。
 * <p>
 * 被此注解标记的方法会被 {@link ToolRegistry}
 * 在启动时自动扫描注册。方法参数需使用 {@link ToolParamDef} 描述。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolDef {

    /** 工具唯一名称，AI 通过此名称调用工具 */
    String name();

    /** 工具功能描述，供 AI 理解何时使用 */
    String description();
}
