package com.helianhealth.iflow.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonValueCodecTest {
    @Test
    void retainsNativeValuesAndDoesNotReconfigureInjectedMapper() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonValueCodec codec = new JsonValueCodec(mapper);
        assertEquals(List.of(Map.of("value", new BigDecimal("1.2301"))),
                codec.parseDocument("[{\"value\":1.2301}]"));
        assertNull(codec.parseDocument("null"));
        assertEquals(false, codec.parseDocument("false"));
        assertInstanceOf(Double.class, mapper.readValue("1.2301", Object.class));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("empty", null);
        assertEquals("{\"empty\":null}", codec.stringify(value));
        assertEquals("null", codec.stringify(null));
    }

    @Test
    void httpParserRejectsTrailingTokensWithoutChangingFunctionParsing() throws Exception {
        JsonValueCodec codec = new JsonValueCodec(new ObjectMapper());
        assertThrows(JsonProcessingException.class, () -> codec.parseDocument("{} {}"));
        assertEquals(Map.of(), codec.parse("{} {}"));
        assertThrows(JsonProcessingException.class, () -> codec.parseDocument(" "));
    }
}
