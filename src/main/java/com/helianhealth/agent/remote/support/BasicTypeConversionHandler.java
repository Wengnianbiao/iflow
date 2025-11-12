package com.helianhealth.agent.remote.support;

import com.helianhealth.agent.enums.ParamType;

/**
 * 基础类型转换处理器
 * 负责将原始值转换为目标参数类型（支持常见基本类型、日期等）
 */
public class BasicTypeConversionHandler implements TypeHandler {

    @Override
    public Object convert(Object rawValue, ParamType targetType) {
        if (rawValue == null) {
            return null;
        }
        if (isTypeMatch(rawValue.getClass(), targetType)) {
            return rawValue;
        }

        // 执行类型转换
        try {
            switch (targetType) {
                case STRING:
                    return String.valueOf(rawValue);
                case INTEGER:
                    return convertToInteger(rawValue);
                case LONG:
                    return convertToLong(rawValue);
                case BOOLEAN:
                    return convertToBoolean(rawValue);
                default:
                    throw new TypeConversionException("不支持的目标类型: " + targetType);
            }
        } catch (NumberFormatException e) {
            throw new TypeConversionException(
                    String.format("值[%s]从类型[%s]转换为[%s]失败",
                            rawValue, rawValue.getClass().getSimpleName(), targetType),
                    e
            );
        }
    }

    // 转换为Integer
    private Integer convertToInteger(Object rawValue) {
        if (rawValue instanceof Number) {
            return ((Number) rawValue).intValue();
        } else if (rawValue instanceof String) {
            return Integer.parseInt(((String) rawValue).trim());
        }
        throw new TypeConversionException("无法将类型[" + rawValue.getClass() + "]转换为INTEGER");
    }

    // 转换为Long
    private Long convertToLong(Object rawValue) {
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        } else if (rawValue instanceof String) {
            return Long.parseLong(((String) rawValue).trim());
        }
        throw new TypeConversionException("无法将类型[" + rawValue.getClass() + "]转换为LONG");
    }

    // 转换为Boolean
    private Boolean convertToBoolean(Object rawValue) {
        if (rawValue instanceof Boolean) {
            return (Boolean) rawValue;
        } else if (rawValue instanceof String) {
            String str = (String) rawValue;
            return "true".equalsIgnoreCase(str) || "1".equals(str);
        } else if (rawValue instanceof Number) {
            return ((Number) rawValue).intValue() != 0;
        }
        throw new TypeConversionException("无法将类型[" + rawValue.getClass() + "]转换为BOOLEAN");
    }


    // 检查原始类型是否与目标类型匹配
    private boolean isTypeMatch(Class<?> rawType, ParamType targetType) {
        switch (targetType) {
            case STRING:
                return String.class.isAssignableFrom(rawType);
            case INTEGER:
                return Integer.class == rawType || int.class == rawType;
            case LONG:
                return Long.class == rawType || long.class == rawType;
            case BOOLEAN:
                return Boolean.class == rawType || boolean.class == rawType;
            default:
                return false;
        }
    }
}
