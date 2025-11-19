package com.helianhealth.agent.remote.webService;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.helianhealth.agent.common.XmlConstants;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.ParamResolver;
import com.helianhealth.agent.remote.manager.HttpApiClientManager;
import com.helianhealth.agent.utils.JsonUtils;
import com.helianhealth.agent.utils.ParamNodeUtils;
import com.helianhealth.agent.utils.XmlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SOAP消息生成器
 * 负责所有SOAP协议相关的处理：元数据解析、消息构建、请求发送和响应处理
 */
@Slf4j
@Component
public class SoapRequestHandler implements ParamResolver {

    private final HttpApiClientManager httpApiClientManager;

    public SoapRequestHandler() {
        this.httpApiClientManager = HttpApiClientManager.getInstance();
    }

    /**
     * 根据metaInfo配置构建完整的SOAP XML消息
     *
     * @param flowNode 工作流节点，包含metaInfo配置
     * @param paramNode 参数节点列表
     * @return 完整的SOAP XML字符串
     */
    public String resolveParamNodes(InterfaceWorkflowNode flowNode, List<ParamTreeNode> paramNode) {
        try {
            JSONObject metaInfo = JsonUtils.parseMetaInfo2JsonObject(flowNode.getMetaInfo());
            String dataType = (String) metaInfo.get(XmlConstants.DATA_TYPE_KEY);
            if (XmlConstants.JSON_KEY.equalsIgnoreCase(dataType)) {
                // 业务数据为JSON
                String paramJson = ParamNodeUtils.paramNode2Json(paramNode);

                return buildSoapFromConfig(metaInfo, paramJson);
            } else {
                // 业务数据为XML格式
                String paramXml = ParamNodeUtils.paramNode2Xml(paramNode.get(0));

                return buildSoapFromConfig(metaInfo, paramXml);
            }

        } catch (Exception e) {
            log.error("构建SOAP消息失败", e);
            throw new RuntimeException("构建SOAP消息失败: " + e.getMessage());
        }
    }

    /**
     * 发送SOAP请求并获取响应
     */
    public String sendSoapRequest(InterfaceWorkflowNode flowNode, String soapMessage) {
        try {
            // 解析metaInfo
            JSONObject metaInfo = parseMetaInfo(flowNode.getMetaInfo());
            String endpointUrl = metaInfo.getString("url");
            if (StringUtils.isEmpty(endpointUrl)) {
                throw new IllegalArgumentException("metaInfo中未配置url");
            }

            // 设置自定义请求头
            Map<String, String> headers = new HashMap<>();
            JSONObject headerConfig = metaInfo.getJSONObject("headers");
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
     * 解析SOAP响应
     */
    public Map<String, Object> parseSoapResponse(InterfaceWorkflowNode flowNode, String response) {
        try {
            // 解析metaInfo获取配置
            String metaInfo = flowNode.getMetaInfo();
            Map<String, Object> metaMap = new HashMap<>();
            if (metaInfo != null && !metaInfo.isEmpty()) {
                metaMap = JsonUtils.parseMetaInfo(metaInfo);
            }

            return XmlUtils.parseResponseXml(response, metaMap);
        } catch (Exception e) {
            log.error("解析SOAP响应失败", e);
            throw new RuntimeException("解析SOAP响应失败: " + e.getMessage());
        }
    }

    /**
     * 解析metaInfo JSON配置
     */
    private JSONObject parseMetaInfo(String metaInfoJson) {
        if (!StringUtils.hasText(metaInfoJson)) {
            return new JSONObject();
        }

        try {
            return JSON.parseObject(metaInfoJson);
        } catch (Exception e) {
            log.warn("解析metaInfo JSON失败，使用空对象: {}", e.getMessage());
            return new JSONObject();
        }
    }

    /**
     * 根据配置构建SOAP消息
     */
    private String buildSoapFromConfig(JSONObject metaInfo, String paramXml) {
        // 构建完整的SOAP信封
        StringBuilder soapMessage = new StringBuilder();

        // 获取envelope配置
        JSONObject envelope = (JSONObject) metaInfo.get("envelope");
        if (envelope == null) {
            throw new IllegalArgumentException("metaInfo中缺少envelope配置");
        }

        // 开始envelope标签
        soapMessage.append("<soapenv:Envelope");

        // 添加命名空间
        for (String key : envelope.keySet()) {
            soapMessage.append(" ").append(key).append("=\"").append(envelope.getString(key)).append("\"");
        }
        soapMessage.append(">\n");

        // 处理header
        String headerConfig = (String) metaInfo.get("header");
        soapMessage.append("  <soapenv:Header");
        soapMessage.append(">");
        soapMessage.append(headerConfig);
        soapMessage.append("</soapenv:Header>\n");

        // 处理body
        String bodyContent = metaInfo.getString("body");
        if (bodyContent == null) {
            throw new IllegalArgumentException("metaInfo中缺少body配置");
        }

        // 替换占位符${inputParam}为实际参数XML
        bodyContent = bodyContent.replace("${inputParam}", escapeXml(paramXml));

        soapMessage.append("  <soapenv:Body>\n");
        soapMessage.append(bodyContent).append("\n");
        soapMessage.append("  </soapenv:Body>\n");

        // 结束envelope标签
        soapMessage.append("</soapenv:Envelope>");

        return soapMessage.toString();
    }

    /**
     * XML转义
     */
    private String escapeXml(String value) {
        return value;
    }
}
