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
     * SpEL 表达式
     */
    EXPRESSION,

    /**
     * Bean 表达式映射（主要用于查体软表字段映射）
     */
    BEAN_EXPRESSION,

    /**
     * 单个 Map 键值对
     */
    SINGLE_MAP
}
