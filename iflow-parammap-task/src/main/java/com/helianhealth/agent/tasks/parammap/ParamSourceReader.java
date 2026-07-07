package com.helianhealth.agent.tasks.parammap;

import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.utils.JsonUtils;

import java.util.Map;

/**
 * 从源 Map 按点号路径读取字段值。
 */
final class ParamSourceReader {

    private ParamSourceReader() {}

    static Object read(Map<String, Object> source, String sourceParamKey, ParamType sourceParamType) {
        if (sourceParamType == ParamType.NONE || sourceParamKey == null || sourceParamKey.isBlank()) {
            return source;
        }
        return readByPath(source, sourceParamKey);
    }

    @SuppressWarnings("unchecked")
    static Object readByPath(Map<String, Object> source, String path) {
        if (source == null || path == null || path.isBlank()) {
            return null;
        }
        Object current = source;
        for (String key : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return JsonUtils.fromBean(map);
        }
        return Map.of();
    }
}
