package com.helianhealth.agent.invocation.utils;

import com.alibaba.fastjson2.JSONObject;
import com.helianhealth.agent.common.base.MetaInfoKeys;
import com.helianhealth.agent.invocation.remote.manager.HttpApiClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class SoapUtils {

    private static final Logger log = LoggerFactory.getLogger(SoapUtils.class);

    private static final HttpApiClientManager httpApiClientManager = HttpApiClientManager.getInstance();

    /**
     * 发送SOAP请求
     * @param metaInfo JSON对象，包含请求地址、方法、请求头等信息
     * @param soapMessage SOAP消息
     * @return SOAP响应
     */
    public static String sendSoapRequest(JSONObject metaInfo, String soapMessage) {
        try {
            String endpointUrl = metaInfo.getString(MetaInfoKeys.URL);
            if (StringUtils.isEmpty(endpointUrl)) {
                throw new IllegalArgumentException("metaInfo中未配置url");
            }

            // 设置自定义请求头
            Map<String, String> headers = new HashMap<>();
            JSONObject headerConfig = metaInfo.getJSONObject(MetaInfoKeys.HEADERS);
            if (headerConfig != null) {
                for (String key : headerConfig.keySet()) {
                    headers.put(key, headerConfig.getString(key));
                }
            }
            // 如果没有设置Content-Type，使用默认值
            if (!headers.containsKey("Content-Type")) {
                headers.put("Content-Type", "text/xml;charset=UTF-8");
            }

            return httpApiClientManager.executePost(endpointUrl, headers, soapMessage);
        } catch (Exception e) {
            log.error("发送SOAP请求失败", e);
            throw new RuntimeException("发送SOAP请求失败: " + e.getMessage());
        }
    }

    /**
     * 发送SOAP请求
     * @param metaInfo Map，包含请求地址、方法、请求头等信息
     * @param soapMessage SOAP消息
     * @return SOAP响应
     */
    @SuppressWarnings("unchecked")
    public static String sendSoapRequest(Map<String, Object> metaInfo, String soapMessage) {
        try {
            String endpointUrl = (String) metaInfo.get(MetaInfoKeys.URL);
            if (StringUtils.isEmpty(endpointUrl)) {
                throw new IllegalArgumentException("metaInfo中未配置url");
            }

            // 构建请求头
            Map<String, String> headers = new HashMap<>();
            // 设置自定义请求头
            Map<String, String> headerConfig = (Map<String, String>) metaInfo.get(MetaInfoKeys.HEADERS);
            if (headerConfig != null) {
                headers.putAll(headerConfig);
            }
            // 如果没有设置Content-Type，使用默认值
            if (!headers.containsKey("Content-Type")) {
                headers.put("Content-Type", "text/xml;charset=UTF-8");
            }

            return httpApiClientManager.executePost(endpointUrl, headers, soapMessage);
        } catch (Exception e) {
            log.error("发送SOAP请求失败", e);
            throw new RuntimeException("发送SOAP请求失败: " + e.getMessage());
        }
    }
}
