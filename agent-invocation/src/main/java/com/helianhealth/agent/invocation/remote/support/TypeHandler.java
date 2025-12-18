package com.helianhealth.agent.invocation.remote.support;

import com.helianhealth.agent.workflow.enums.ParamType;

/**
 * 类型转换处理器接口
 */
public interface TypeHandler {
    /**
     * 将原始值转换为目标类型
     * @param rawValue 原始值（可能为null）
     * @param targetType 目标参数类型
     * @return 转换后的值
     * @throws TypeConversionException 转换失败时抛出
     */
    Object convert(Object rawValue, ParamType targetType);
}
