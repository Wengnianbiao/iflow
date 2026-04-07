package com.helianhealth.agent.invocation.remote.webService;

import com.alibaba.fastjson2.JSONObject;
import com.helianhealth.agent.common.base.MetaInfoKeys;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.ParamResolver;
import com.helianhealth.agent.invocation.remote.manager.HttpApiClientManager;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.utils.ParamNodeUtils;
import com.helianhealth.agent.common.utils.XmlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SOAP 消息处理器
 * 负责所有 SOAP 协议相关的处理：元数据解析、消息构建、请求发送和响应处理
 */
@Slf4j
@Component
public class SoapRequestHandler implements ParamResolver {

    private final HttpApiClientManager httpApiClientManager;

    private static final String INPUT_PARAM_PLACEHOLDER_KEY = "${inputParam}";

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
    public String resolveParamNodes(WorkflowNode flowNode, List<ParamTreeNode> paramNode) {
        try {
            JSONObject metaInfo = JsonUtils.parseMetaInfo2JsonObject(flowNode.getMetaInfo());
            String dataType = metaInfo.getString(MetaInfoKeys.DATA_TYPE);
            if (MetaInfoKeys.DATA_TYPE_JSON.equalsIgnoreCase(dataType)) {
                // 业务数据为JSON
                String paramJson = ParamNodeUtils.paramNode2Json(paramNode);

                return buildSoapFromConfig(metaInfo, paramJson);
            } else {
                // 业务数据为XML格式
                String paramXml;
                if (paramNode.size() > 1) {
                    // 多个节点，无根标签包裹的情况
                    paramXml = buildXmlFromMultipleNodes(paramNode);
                } else {
                    // 单个节点
                    paramXml = ParamNodeUtils.paramNode2Xml(paramNode.get(0));
                }

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
    public String sendSoapRequest(WorkflowNode flowNode, String soapMessage) {
        try {
            // 解析metaInfo
            JSONObject metaInfo = JsonUtils.parseMetaInfo2JsonObject(flowNode.getMetaInfo());
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
     * 解析SOAP响应
     */
    public Map<String, Object> parseSoapResponse(WorkflowNode flowNode, String response) {
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
     * 根据配置构建SOAP消息
     */
    private String buildSoapFromConfig(JSONObject metaInfo, String paramXml) {
        String soupTemplate = metaInfo.getString(MetaInfoKeys.SOAP_TEMPLATE);
        // 没有模板说明不需要嵌套SOAP信封
        if (soupTemplate == null || soupTemplate.isEmpty()) {
            return paramXml;
        }

        // 替换占位符${inputParam}为实际参数XML
        return soupTemplate.replace(INPUT_PARAM_PLACEHOLDER_KEY, paramXml);
    }

    /**
     * 将多个ParamTreeNode转换为XML字符串（无根标签包裹）
     */
    private String buildXmlFromMultipleNodes(List<ParamTreeNode> paramNodes) {
        if (paramNodes == null || paramNodes.isEmpty()) {
            return "";
        }

        StringBuilder xml = new StringBuilder();
        for (ParamTreeNode node : paramNodes) {
            String nodeXml = ParamNodeUtils.paramNode2Xml(node);
            xml.append(nodeXml);
        }

        return xml.toString();
    }
}
