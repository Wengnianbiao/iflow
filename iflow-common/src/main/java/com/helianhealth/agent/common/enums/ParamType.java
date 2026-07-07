package com.helianhealth.agent.common.enums;

import lombok.Getter;

/**
 * 参数类型枚举
 */
@Getter
public enum ParamType {

    /**
     * 字符串
     */
    STRING,

    /**
     * 整数
     */
    INTEGER,

    /**
     * 长整型
     */
    LONG,

    /**
     * 布尔值
     */
    BOOLEAN,

    /**
     * 双精度浮点数
     */
    DOUBLE,

    /**
     * 对象
     */
    OBJECT,

    /**
     * 数组
     */
    ARRAY,

    /**
     * 纯数组（接口入参就是数组）
     */
    PURE_ARRAY,

    /**
     * 无类型
     */
    NONE
}
