package com.helianhealth.agent.tasks.mapping.enums;

import lombok.Getter;

/**
 * 映射规则类型枚举
 */
@Getter
public enum MappingType {

    /**
     * 固定值
     */
    CONSTANT,

    /**
     * 名称映射
     */
    NAME,

    /**
     * GraalJS 表达式（通过 {@code $} 读取映射上下文）
     */
    EXPRESSION
}
