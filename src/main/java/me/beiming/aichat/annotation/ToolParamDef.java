package me.beiming.aichat.annotation;

import java.lang.annotation.*;

/**
 * 描述工具方法的参数元数据，用于生成 OpenAI Function Calling 的 JSON Schema。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolParamDef {

    /** 参数名称 */
    String name();

    /** JSON Schema 类型：string, number, integer, boolean, array, object */
    String type() default "string";

    /** 参数描述 */
    String description();

    /** 是否必填 */
    boolean required() default false;

    /** 枚举值列表（如有），用于生成 JSON Schema 的 enum 字段 */
    String[] enumValues() default {};
}
