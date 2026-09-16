package com.helianhealth.agent.core.execution.function;

/**
 * FUNCTION 任务允许调用的内置函数。
 */
public enum FunctionType {
    JSON_PARSE,
    JSON_STRINGIFY,
    XML_PARSE,
    XML_STRINGIFY;

    public boolean requiresStringInput() {
        return this == JSON_PARSE || this == XML_PARSE;
    }

    public static FunctionType from(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("FUNCTION input 'function' must be provided");
        }
        try {
            return valueOf(text.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported FUNCTION function: " + text.trim());
        }
    }
}
