package com.helianhealth.agent.common.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Agent Activate扩展注解
 *
 * 用于标识扩展的自动激活条件，当调用getActivateExtensions时，
 * 会根据此注解的条件筛选并排序扩展实现。
 *
 * 使用示例：
 * <pre>
 * // 当存在token参数时生效
 * {@code @AgentActivate(value = "token")}
 * public class TokenFilter implements Filter { }
 *
 * // 指定排序（值越小越靠前）
 * {@code @AgentActivate(order = 100)}
 * public class LogFilter implements Filter { }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AgentActivate {

    /**
     * 参数条件
     *
     * 只有当上下文中存在指定key的参数时，扩展才会被激活
     * 空数组表示不限制参数
     *
     * @return 参数key数组
     */
    String[] value() default {};

    /**
     * 排序值
     *
     * 值越小优先级越高，默认为0
     *
     * @return 排序值
     */
    int order() default 0;

    /**
     * 排序在指定扩展之前
     *
     * @return 扩展名数组
     */
    String[] before() default {};

    /**
     * 排序在指定扩展之后
     *
     * @return 扩展名数组
     */
    String[] after() default {};
}
