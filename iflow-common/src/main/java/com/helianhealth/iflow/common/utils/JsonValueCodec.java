package com.helianhealth.iflow.common.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 网关与 FUNCTION 共用的 JSON 值转换；保留数组、标量、null 和十进制精度。 */
public final class JsonValueCodec {

    private final ObjectMapper mapper;

    public JsonValueCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public Object parse(String input) throws JsonProcessingException {
        return normalize(mapper.readValue(input, Object.class));
    }

    /** HTTP 报文必须恰好包含一个完整 JSON 值。 */
    public Object parseDocument(String input) throws JsonProcessingException {
        return normalize(mapper.readerFor(Object.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(input));
    }

    public String stringify(Object value) throws JsonProcessingException {
        return mapper.writeValueAsString(value);
    }

    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>(map.size());
            map.forEach((key, item) -> result.put(String.valueOf(key), normalize(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(item -> result.add(normalize(item)));
            return result;
        }
        return value;
    }
}
