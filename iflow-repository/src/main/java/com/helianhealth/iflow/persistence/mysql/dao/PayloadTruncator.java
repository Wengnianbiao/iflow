package com.helianhealth.iflow.persistence.mysql.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;

/**
 * 仅供执行记录落库使用：把超过字符上限的 JSON 值按字段截断，保留对象和数组结构。
 * 不修改传入的容器或其嵌套数据。
 */
final class PayloadTruncator {

    static final int MAX_CHARS = 1_000_000;

    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadTruncator.class);

    private final ObjectMapper objectMapper;
    private final int maxChars;

    PayloadTruncator(ObjectMapper objectMapper) {
        this(objectMapper, MAX_CHARS);
    }

    PayloadTruncator(ObjectMapper objectMapper, int maxChars) {
        if (maxChars < 2) {
            throw new IllegalArgumentException("maxChars must be at least 2");
        }
        this.objectMapper = objectMapper;
        this.maxChars = maxChars;
    }

    Object truncate(String label, Object value) {
        if (value == null) {
            return null;
        }
        JsonNode node = objectMapper.valueToTree(value);
        int originalChars = length(node);
        if (originalChars <= maxChars) {
            return value;
        }
        JsonNode truncated = fit(node, maxChars);
        int truncatedChars = length(truncated);
        LOGGER.warn("{}超过 {} 字符，已按字段截断：{} -> {}", label, maxChars, originalChars, truncatedChars);
        return objectMapper.convertValue(truncated, Object.class);
    }

    private JsonNode fit(JsonNode node, int budget) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return node == null ? NullNode.getInstance() : node;
        }
        if (budget < 2) {
            return emptyValue(node);
        }
        if (length(node) <= budget) {
            return node;
        }
        if (node.isTextual()) {
            return fitText(node.asText(), budget);
        }
        if (node.isArray()) {
            return fitArray((ArrayNode) node, budget);
        }
        if (node.isObject()) {
            return fitObject((ObjectNode) node, budget);
        }
        return NullNode.getInstance();
    }

    private JsonNode fitText(String text, int budget) {
        String marker = "…(已截断,原" + text.length() + "字符)";
        int withMarker = longestPrefix(text, marker, budget);
        if (withMarker >= 0) {
            return new TextNode(text.substring(0, withMarker) + marker);
        }
        return new TextNode(text.substring(0, longestPrefix(text, "", budget)));
    }

    private int longestPrefix(String text, String suffix, int budget) {
        int low = 0;
        int high = text.length();
        int best = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int encoded = length(new TextNode(text.substring(0, mid) + suffix));
            if (encoded <= budget) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private JsonNode fitArray(ArrayNode node, int budget) {
        ArrayNode kept = objectMapper.createArrayNode();
        if (budget < 2) {
            return kept;
        }
        for (JsonNode element : node) {
            int comma = kept.isEmpty() ? 0 : 1;
            int elementBudget = budget - length(kept) - comma;
            if (elementBudget < 2) {
                break;
            }
            JsonNode fitted = length(element) <= elementBudget ? element : fit(element, elementBudget);
            if (length(kept) + comma + length(fitted) > budget) {
                break;
            }
            kept.add(fitted);
        }
        return kept;
    }

    private JsonNode fitObject(ObjectNode node, int budget) {
        ObjectNode result = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            result.set(field.getKey(), field.getValue());
        }
        while (length(result) > budget && result.size() > 0) {
            String largestKey = largestField(result);
            JsonNode current = result.get(largestKey);
            int currentLen = length(current);
            int rest = Math.max(0, length(result) - currentLen);
            int fieldBudget = budget - rest;
            if (fieldBudget < 2) {
                result.remove(largestKey);
                continue;
            }
            JsonNode fitted = fit(current, fieldBudget);
            if (length(fitted) >= currentLen) {
                result.remove(largestKey);
                continue;
            }
            result.set(largestKey, fitted);
        }
        return result;
    }

    private String largestField(ObjectNode node) {
        String largestKey = null;
        int largestLen = -1;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            int fieldLen = length(field.getValue());
            if (fieldLen > largestLen) {
                largestLen = fieldLen;
                largestKey = field.getKey();
            }
        }
        return largestKey;
    }

    private JsonNode emptyValue(JsonNode node) {
        if (node.isArray()) {
            return objectMapper.createArrayNode();
        }
        if (node.isObject()) {
            return objectMapper.createObjectNode();
        }
        return new TextNode("");
    }

    private int length(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node).length();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not measure payload length", ex);
        }
    }
}
