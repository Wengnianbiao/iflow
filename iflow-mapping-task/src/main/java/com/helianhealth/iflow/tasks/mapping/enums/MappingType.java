package com.helianhealth.iflow.tasks.mapping.enums;

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
    EXPRESSION,

    /** 按子字段构造对象。 */
    OBJECT,

    /** 遍历源数组，用元素模板构造每一项。 */
    ARRAY_MAP,

    /** 按有序元素模板构造固定数组。 */
    ARRAY_FIXED
}
