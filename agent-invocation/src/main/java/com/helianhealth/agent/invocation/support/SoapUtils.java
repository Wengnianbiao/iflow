package com.helianhealth.agent.invocation.support;

import com.helianhealth.agent.common.base.MetaInfoKeys;
import com.helianhealth.agent.common.exception.AgentConfigException;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentInvocationException;
import com.helianhealth.agent.common.utils.XmlUtils;
import com.helianhealth.agent.invocation.remote.manager.HttpApiClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class SoapUtils {

    private static final Logger log = LoggerFactory.getLogger(SoapUtils.class);

    private static final HttpApiClientManager httpApiClientManager = HttpApiClientManager.getInstance();

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
            if (endpointUrl == null || endpointUrl.isEmpty()) {
                throw new AgentConfigException(AgentErrorCode.WS_CONFIG_MISSING, "metaInfo中未配置url");
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
            throw new AgentInvocationException(AgentErrorCode.WS_CALL_FAILED, e.getMessage(), e);
        }
    }

    /**
     * 解析SOAP/XML响应
     * @param responseXml 原始XML响应字符串
     * @param metaInfo 元信息配置，支持以下key：
     *                 elementName - 业务数据标签名定位（如SOAP Body内的目标节点）
     *                 isCDATA - 是否从CDATA/文本内容中提取，默认true
     *                 dataType - 内容类型（json/xml），默认xml
     *                 innerElementName - XML内容中的二级标签定位
     *                 escape - 是否先反转义再解析
     *                 objectTag - 指定按对象解析的标签名集合
     * @return 解析后的Map
     */
    public static Map<String, Object> parseSoapResponse(String responseXml, Map<String, Object> metaInfo) {
        try {
            return XmlUtils.parseResponseXml(responseXml, metaInfo != null ? metaInfo : new HashMap<>());
        } catch (Exception e) {
            log.error("解析SOAP响应失败", e);
            throw new AgentInvocationException(AgentErrorCode.WS_RESPONSE_PARSE_ERROR, e.getMessage(), e);
        }
    }
}
