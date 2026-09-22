package com.helianhealth.iflow.core.execution.function;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.helianhealth.iflow.core.execution.script.XmlMapParser;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ObjectMapper objectMapper;

    @Autowired
    public FunctionExecutor(
            ObjectMapper objectMapper,
            @Value("${iflow.function.max-input-chars:" + DEFAULT_MAX_INPUT_CHARS + "}")
                    int maxInputChars) {
        if (maxInputChars <= 0) {
            throw new IllegalArgumentException("iflow.function.max-input-chars must be positive");
        }
        this.maxInputChars = maxInputChars;
        this.objectMapper = configure(objectMapper);
    }

    public FunctionExecutor(int maxInputChars) {
        this(defaultObjectMapper(), maxInputChars);
    }

    private static ObjectMapper defaultObjectMapper() {
        return JsonMapper.builder().build();
    }

    private static ObjectMapper configure(ObjectMapper objectMapper) {
        return objectMapper
                .copy()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public Object execute(FunctionType functionType, Object value) {
        return execute(functionType, value, null);
    }

    public Object execute(FunctionType functionType, Object value, String rootName) {
        return switch (functionType) {
            case JSON_PARSE -> parseJson(requireParseInput(value));
            case XML_PARSE -> parseXml(requireParseInput(value));
            case JSON_STRINGIFY -> stringifyJson(value);
            case XML_STRINGIFY -> stringifyXml(value, rootName);
        };
    }

    private String requireParseInput(Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("FUNCTION input 'value' must be a String or null");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("FUNCTION input 'value' must not be blank");
        }
        enforceMaxChars(text);
        return text;
    }

    private void enforceMaxChars(String text) {
        if (text.length() > maxInputChars) {
            throw new IllegalArgumentException(
                    "FUNCTION input exceeds iflow.function.max-input-chars=" + maxInputChars);
        }
    }

    private Object parseJson(String input) {
        final Object parsed;
        try {
            parsed = objectMapper.readValue(input, Object.class);
        } catch (Exception ignored) {
            throw new IllegalArgumentException("FUNCTION JSON_PARSE failed: invalid JSON");
        }
        return normalize(parsed);
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

    private String stringifyJson(Object value) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            throw new IllegalArgumentException("FUNCTION JSON_STRINGIFY failed: invalid JSON");
        }
        enforceMaxChars(json);
        return json;
    }

    private String stringifyXml(Object value, String rootName) {
        try {
            String xml =
                    rootName == null || rootName.isBlank()
                            ? XmlMapParser.stringify(value)
                            : XmlMapParser.stringify(rootName, value);
            if (xml == null) {
                return null;
            }
            enforceMaxChars(xml);
            return xml;
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("FUNCTION input exceeds")) {
                throw e;
            }
            throw new IllegalArgumentException("FUNCTION XML_STRINGIFY failed: invalid XML");
        } catch (Exception ignored) {
            throw new IllegalArgumentException("FUNCTION XML_STRINGIFY failed: invalid XML");
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
