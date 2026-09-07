package com.helianhealth.agent.core.execution.function;

import com.alibaba.fastjson2.JSON;
import com.helianhealth.agent.core.execution.script.XmlMapParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行 FUNCTION 任务的受控、纯 Java 内置函数。
 */
@Component
public class FunctionExecutor {

    public static final int DEFAULT_MAX_INPUT_CHARS = 5_000_000;

    private final int maxInputChars;

    public FunctionExecutor(
            @Value("${iflow.function.max-input-chars:" + DEFAULT_MAX_INPUT_CHARS + "}")
                    int maxInputChars) {
        if (maxInputChars <= 0) {
            throw new IllegalArgumentException("iflow.function.max-input-chars must be positive");
        }
        this.maxInputChars = maxInputChars;
    }

    public Map<String, Object> execute(FunctionType functionType, String input) {
        if (input.isBlank()) {
            throw new IllegalArgumentException("FUNCTION input 'value' must not be blank");
        }
        if (input.length() > maxInputChars) {
            throw new IllegalArgumentException(
                    "FUNCTION input exceeds iflow.function.max-input-chars=" + maxInputChars);
        }

        return switch (functionType) {
            case JSON_PARSE -> parseJson(input);
            case XML_PARSE -> parseXml(input);
        };
    }

    private Map<String, Object> parseJson(String input) {
        final Object parsed;
        try {
            parsed = JSON.parse(input);
        } catch (Exception ignored) {
            throw new IllegalArgumentException("FUNCTION JSON_PARSE failed: invalid JSON");
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                    "FUNCTION JSON_PARSE requires a JSON object root");
        }
        return normalizeMap(map);
    }

    private Map<String, Object> parseXml(String input) {
        try {
            Map<String, Object> parsed = XmlMapParser.parse(input);
            if (parsed == null) {
                throw new IllegalArgumentException("empty XML");
            }
            return parsed;
        } catch (Exception ignored) {
            throw new IllegalArgumentException("FUNCTION XML_PARSE failed: invalid XML");
        }
    }

    private Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>(source.size());
        source.forEach((key, value) -> result.put(String.valueOf(key), normalize(value)));
        return result;
    }

    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(item -> result.add(normalize(item)));
            return result;
        }
        return value;
    }
}
