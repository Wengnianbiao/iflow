package com.helianhealth.agent.common.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonUtils {

    public static String toJsonString(Object object) {
        try {
            return JSON.toJSONString(object);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("JSON序列化失败: " + e.getMessage(), e);
        }
    }

    public static Map<String, String> parseStringMap(String jsonString) {
        if (jsonString == null) {
            throw new IllegalArgumentException("JSON字符串不能为空");
        }
        try {
            return JSON.parseObject(jsonString, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON解析失败: " + e.getMessage(), e);
        }
    }

    public static Map<String, Object> parseObjectMap(String jsonString) {
        if (jsonString == null) {
            throw new IllegalArgumentException("JSON字符串不能为空");
        }
        try {
            return JSON.parseObject(jsonString, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 Java Bean / DTO 转换为新的 Map<String, Object>。
     *
     * @param bean Bean实例
     * @return 转换后的Map；当入参为null时返回null
     */
    public static Map<String, Object> fromBean(Object bean) {
        if (bean == null) {
            return null;
        }

        try {
            Object parsed = JSON.parse(JSON.toJSONString(bean));
            if (!(parsed instanceof JSONObject)) {
                throw new IllegalArgumentException("Bean转换结果不是JSON对象");
            }
            return new LinkedHashMap<>((JSONObject) parsed);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Bean转换为Map失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 JSONObject 转换为新的 Map<String, Object>。
     *
     * @param jsonObject JSONObject实例
     * @return 转换后的Map；当入参为null时返回null
     */
    public static Map<String, Object> fromJsonObject(JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }

        return new LinkedHashMap<>(jsonObject);
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
            throw new IllegalArgumentException("Map深拷贝失败: " + e.getMessage(), e);
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
            throw new IllegalArgumentException("metaInfo解析失败: " + e.getMessage(), e);
        }
    }
}
