package com.helianhealth.iflow.core.execution.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.helianhealth.iflow.common.utils.JsonValueCodec;
import com.helianhealth.iflow.common.utils.XmlMapParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FunctionExecutor {

    public static final int DEFAULT_MAX_INPUT_CHARS = 10_000_000;

    private final int maxInputChars;
    private final JsonValueCodec jsonCodec;

    @Autowired
    public FunctionExecutor(
            ObjectMapper objectMapper,
            @Value("${iflow.function.max-input-chars:" + DEFAULT_MAX_INPUT_CHARS + "}")
                    int maxInputChars) {
        if (maxInputChars <= 0) {
            throw new IllegalArgumentException("iflow.function.max-input-chars must be positive");
        }
        this.maxInputChars = maxInputChars;
        this.jsonCodec = new JsonValueCodec(objectMapper);
    }

    public FunctionExecutor(int maxInputChars) {
        this(defaultObjectMapper(), maxInputChars);
    }

    private static ObjectMapper defaultObjectMapper() {
        return JsonMapper.builder().build();
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
        try {
            return jsonCodec.parse(input);
        } catch (Exception ignored) {
            throw new IllegalArgumentException("FUNCTION JSON_PARSE failed: invalid JSON");
        }
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
            json = jsonCodec.stringify(value);
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

}
