package com.helianhealth.iflow.persistence.mysql.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadTruncatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void leavesPayloadWithinLimitUntouched() {
        PayloadTruncator truncator = new PayloadTruncator(mapper);
        Map<String, Object> value = Map.of("code", 200, "body", "x".repeat(500_000));

        assertSame(value, truncator.truncate("输出", value));
    }

    @Test
    void truncatesOnlyTheOversizedStringField() throws Exception {
        PayloadTruncator truncator = new PayloadTruncator(mapper, 80);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("statusCode", 200);
        value.put("reasonPhrase", "OK");
        value.put("body", "x".repeat(200));

        Object truncated = truncator.truncate("输出", value);
        JsonNode json = mapper.valueToTree(truncated);

        assertTrue(mapper.writeValueAsString(truncated).length() <= 80);
        assertEquals(200, json.get("statusCode").asInt());
        assertEquals("OK", json.get("reasonPhrase").asText());
        assertTrue(json.get("body").isTextual());
        assertTrue(json.get("body").asText().contains("已截断"));
        assertTrue(json.get("body").asText().startsWith("x"));
    }

    @Test
    void shortensLargeArrayFieldAndKeepsSiblingFields() throws Exception {
        PayloadTruncator truncator = new PayloadTruncator(mapper, 180);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "ITEM" + i);
            item.put("name", "血常规");
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("data", items);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("statusCode", 200);
        value.put("body", body);

        Object truncated = truncator.truncate("输出", value);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(truncated));

        assertTrue(mapper.writeValueAsString(truncated).length() <= 180);
        assertEquals(200, json.get("statusCode").asInt());
        assertEquals(200, json.get("body").get("code").asInt());
        assertTrue(json.get("body").get("data").isArray());
        assertTrue(json.get("body").get("data").size() > 0);
        assertTrue(json.get("body").get("data").size() < items.size());
        JsonNode first = json.get("body").get("data").get(0);
        assertEquals("ITEM0", first.get("id").asText());
        assertEquals("血常规", first.get("name").asText());
    }

    @Test
    void truncatedStringWithQuotesStaysValidJson() throws Exception {
        PayloadTruncator truncator = new PayloadTruncator(mapper, 60);
        Map<String, Object> value = Map.of("note", "a\"b\\".repeat(40), "ok", true);

        Object truncated = truncator.truncate("输出", value);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(truncated));

        assertTrue(mapper.writeValueAsString(truncated).length() <= 60);
        assertEquals(true, json.get("ok").asBoolean());
        assertTrue(json.get("note").isTextual());
        assertInstanceOf(Map.class, truncated);
    }
}
