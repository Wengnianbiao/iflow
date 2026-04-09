package com.helianhealth.agent.common.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;

import java.util.HashMap;
import java.util.Map;

public class JsonUtils {

    public static String toJsonString(Object object) {
        try {
            return JSON.toJSONString(object);
        }
        catch (Exception e) {
            throw new AgentException(AgentErrorCode.JSON_SERIALIZE_ERROR, e.getMessage(), e);
        }
    }

    public static Map<String, String> fromJsonStringToMap(String jsonString) {
        if (jsonString == null) {
            throw new AgentException(AgentErrorCode.JSON_PARSE_ERROR, "JSON string cannot be null");
        }
        try {
            return JSON.parseObject(jsonString, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new AgentException(AgentErrorCode.JSON_PARSE_ERROR, e.getMessage(), e);
        }
    }

    public static Map<String, Object> fromJsonStringToObjectMap(String jsonString) {
        if (jsonString == null) {
            throw new AgentException(AgentErrorCode.JSON_PARSE_ERROR, "JSON string cannot be null");
        }
        try {
            return JSON.parseObject(jsonString, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new AgentException(AgentErrorCode.JSON_PARSE_ERROR, e.getMessage(), e);
        }
    }

    /**
     * 将不同类型的对象转换为 Map<String, Object>
     * 支持 JSONObject、String、Map 等类型
     * @param obj 输入对象
     * @return 转换后的 Map
     */
    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return null;
        }

        try {
            // 如果已经是 Map，直接返回
            if (obj instanceof Map) {
                return (Map<String, Object>) obj;
            }

            // 如果是字符串，解析为 Map
            if (obj instanceof String) {
                String str = (String) obj;
                if (str.trim().isEmpty()) {
                    return null;
                }
                return JSON.parseObject(str, new TypeReference<Map<String, Object>>() {});
            }

            // 其他类型，先序列化再反序列化（向后兼容）
            String jsonStr = JSON.toJSONString(obj);
            return JSON.parseObject(jsonStr, new TypeReference<Map<String, Object>>() {});

        } catch (Exception e) {
            throw new AgentException(AgentErrorCode.JSON_PARSE_ERROR, e.getMessage(), e);
        }
    }

    /**
     * 深度复制 Map<String, Object>（支持嵌套结构：内部 Map、List 等）
     * 避免修改副本时影响原始 Map，适用于需要隔离数据的场景（如流程处理、多线程操作）
     * @param originalMap 原始 Map（可为 null，返回 null）
     * @return 深度复制后的新 Map
     */
    public static Map<String, Object> deepCopyMap(Map<String, Object> originalMap) {
        if (originalMap == null) {
            return null;
        }

        try {
            // 序列化：将原始 Map 转为 JSON 字符串（保留所有嵌套结构）
            String jsonStr = JSON.toJSONString(originalMap);
            // 反序列化：将 JSON 字符串转为新的 Map（重新创建所有嵌套对象，实现深度隔离）
            return JSON.parseObject(jsonStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new AgentException(AgentErrorCode.JSON_DEEP_COPY_ERROR, e.getMessage(), e);
        }
    }

    /**
     * 解析metaInfo JSON字符串为Map<String, Object>
     *
     * @param metaInfo 包含元信息的JSON字符串
     * @return 解析后的Map对象，如果metaInfo为空则返回空Map
     */
    public static Map<String, Object> parseMetaInfo(String metaInfo) {
        if (metaInfo == null || metaInfo.isEmpty()) {
            return new HashMap<>();
        }

        try {
            return JSON.parseObject(metaInfo, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new AgentException(AgentErrorCode.JSON_PARSE_ERROR, "Error parsing metaInfo: " + e.getMessage(), e);
        }
    }

    /**
     * 解析metaInfo JSON字符串为JSONObject
     *
     * @param metaInfo 包含元信息的JSON字符串
     * @return 解析后的JSONObject
     */
    public static JSONObject parseMetaInfo2JsonObject(String metaInfo) {
        if (metaInfo == null || metaInfo.isEmpty()) {
            return new JSONObject();
        }

        try {
            return JSON.parseObject(metaInfo);
        } catch (Exception e) {
            throw new AgentException(AgentErrorCode.JSON_PARSE_ERROR, "Error parsing metaInfo: " + e.getMessage(), e);
        }
    }
}

