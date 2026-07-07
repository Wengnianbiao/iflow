package com.helianhealth.agent.tasks.parammap;

import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.exception.WorkFlowConfigException;
import com.helianhealth.agent.common.exception.WorkFlowErrorCode;

/**
 * 标量类型转换。
 */
final class ParamTypeConverter {

    private ParamTypeConverter() {}

    static Object convert(Object rawValue, ParamType targetType) {
        if (rawValue == null || targetType == null) {
            return null;
        }
        if (isScalar(targetType) && isTypeMatch(rawValue, targetType)) {
            return rawValue;
        }
        return switch (targetType) {
            case STRING -> String.valueOf(rawValue);
            case INTEGER -> toInteger(rawValue);
            case LONG -> toLong(rawValue);
            case BOOLEAN -> toBoolean(rawValue);
            case DOUBLE -> toDouble(rawValue);
            default -> rawValue;
        };
    }

    private static boolean isScalar(ParamType type) {
        return type == ParamType.STRING
                || type == ParamType.INTEGER
                || type == ParamType.LONG
                || type == ParamType.BOOLEAN
                || type == ParamType.DOUBLE;
    }

    private static boolean isTypeMatch(Object rawValue, ParamType targetType) {
        return switch (targetType) {
            case STRING -> rawValue instanceof String;
            case INTEGER -> rawValue instanceof Integer;
            case LONG -> rawValue instanceof Long;
            case BOOLEAN -> rawValue instanceof Boolean;
            case DOUBLE -> rawValue instanceof Double;
            default -> false;
        };
    }

    private static Integer toInteger(Object rawValue) {
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        if (rawValue instanceof String text) {
            return Integer.parseInt(text.trim());
        }
        throw conversionError(rawValue, ParamType.INTEGER);
    }

    private static Long toLong(Object rawValue) {
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        if (rawValue instanceof String text) {
            return Long.parseLong(text.trim());
        }
        throw conversionError(rawValue, ParamType.LONG);
    }

    private static Double toDouble(Object rawValue) {
        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }
        if (rawValue instanceof String text) {
            return Double.parseDouble(text.trim());
        }
        throw conversionError(rawValue, ParamType.DOUBLE);
    }

    private static Boolean toBoolean(Object rawValue) {
        if (rawValue instanceof Boolean bool) {
            return bool;
        }
        if (rawValue instanceof Number number) {
            return number.intValue() != 0;
        }
        if (rawValue instanceof String text) {
            return "true".equalsIgnoreCase(text) || "1".equals(text);
        }
        throw conversionError(rawValue, ParamType.BOOLEAN);
    }

    private static WorkFlowConfigException conversionError(Object rawValue, ParamType targetType) {
        return new WorkFlowConfigException(
                WorkFlowErrorCode.TYPE_CONVERSION_ERROR,
                "无法将类型[" + rawValue.getClass() + "]转换为" + targetType);
    }
}
