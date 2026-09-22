package com.helianhealth.iflow.core.execution.evaluators;

import java.util.Map;

/**
 * 表达式求值器。
 *
 * <p>用于 INLINE / SWITCH 等任务对入参做即席求值。typename 由实现方在 Spring Bean
 * 名字上声明（如 {@code @Component("spel")}），调用方按 evaluatorType 路由。
 */
public interface Evaluator {

    /**
     * 对表达式求值。
     *
     * @param expression 表达式字符串
     * @param input      可见的根上下文（通常为任务 inputData）
     * @return 求值结果（可能是任意类型，调用方按约定转型）
     */
    Object evaluate(String expression, Map<String, Object> input);
}
