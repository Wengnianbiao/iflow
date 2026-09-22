package com.helianhealth.iflow.ai.mcp.dispatcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Tool Dispatcher 共用工具方法。
 *
 * <p>所有 Dispatcher 都通过这里取 action / 必填字段 / 反序列化子对象，
 * 不直接持有 ObjectMapper，避免每个 Dispatcher 重复造轮子。</p>
 */
public abstract class McpDispatcherSupport {

    protected final ObjectMapper objectMapper;

    protected McpDispatcherSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 取 action 字段，缺失抛 IllegalArgumentException
     */
    protected String requireAction(JsonNode arguments) {
        String action = optString(arguments, "action");
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少必填字段: action");
        }
        return action.trim();
    }

    /**
     * 取 String 字段，空串视为 null
     */
    protected String optString(JsonNode arguments, String key) {
        if (arguments == null || !arguments.hasNonNull(key)) {
            return null;
        }
        String value = arguments.get(key).asText();
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * 取必填 String 字段，缺失抛 IllegalArgumentException
     */
    protected String requireString(JsonNode arguments, String key) {
        String value = optString(arguments, key);
        if (value == null) {
            throw new IllegalArgumentException("缺少必填字段: " + key);
        }
        return value;
    }

    /**
     * 取 Integer 字段
     */
    protected Integer optInt(JsonNode arguments, String key) {
        if (arguments == null || !arguments.hasNonNull(key)) {
            return null;
        }
        JsonNode node = arguments.get(key);
        if (node.isInt() || node.isLong() || node.isShort()) {
            return node.intValue();
        }
        if (node.isTextual()) {
            String s = node.asText();
            return s.isEmpty() ? null : Integer.valueOf(s);
        }
        return null;
    }

    protected Integer requireInt(JsonNode arguments, String key) {
        Integer value = optInt(arguments, key);
        if (value == null) {
            throw new IllegalArgumentException("缺少必填字段: " + key);
        }
        return value;
    }

    /**
     * 取 int 字段，缺失走默认值
     */
    protected int optIntOr(JsonNode arguments, String key, int defaultValue) {
        Integer value = optInt(arguments, key);
        return value == null ? defaultValue : value;
    }

    /**
     * 取 Boolean 字段
     */
    protected Boolean optBool(JsonNode arguments, String key) {
        if (arguments == null || !arguments.hasNonNull(key)) {
            return null;
        }
        return arguments.get(key).asBoolean();
    }

    /**
     * 把 arguments 中的子节点反序列化为指定类型
     */
    protected <T> T convertSubNode(JsonNode arguments, String key, Class<T> type) {
        JsonNode node = arguments == null ? null : arguments.get(key);
        if (node == null || node instanceof NullNode) {
            return null;
        }
        return objectMapper.convertValue(node, type);
    }

    protected <T> List<T> convertSubNodeList(JsonNode arguments, String key, Class<T> elementType) {
        JsonNode node = arguments == null ? null : arguments.get(key);
        if (node == null || node instanceof NullNode || !node.isArray()) {
            return null;
        }
        return objectMapper.convertValue(node,
                objectMapper.getTypeFactory().constructCollectionType(java.util.ArrayList.class, elementType));
    }

    /**
     * 把整体 arguments（去掉 action 字段后）反序列化为指定类型，用于 create/update 类 action
     */
    protected <T> T convertWithoutAction(JsonNode arguments, Class<T> type) {
        if (arguments == null) {
            return null;
        }
        ObjectNode copy = arguments.deepCopy();
        copy.remove("action");
        return objectMapper.convertValue(copy, type);
    }

    @SuppressWarnings("unused")
    protected <T> T convertNode(JsonNode node, TypeReference<T> typeReference) {
        if (node == null || node instanceof NullNode) {
            return null;
        }
        return objectMapper.convertValue(node, typeReference);
    }
}
