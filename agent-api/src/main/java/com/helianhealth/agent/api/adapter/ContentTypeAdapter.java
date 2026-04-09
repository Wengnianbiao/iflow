package com.helianhealth.agent.api.adapter;

import com.helianhealth.agent.common.enums.WorkflowContentType;
import com.helianhealth.agent.common.exception.AgentConfigException;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.common.utils.XmlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 协议适配器
 * 负责将不同协议格式（JSON/XML）与内部 Map 格式之间进行双向转换
 */
@Component
@Slf4j
public class ContentTypeAdapter {

    /**
     * 将请求体适配为内部 Map 格式
     * 支持 JSON / XML 等多种协议
     *
     * @param workflow 工作流配置（包含 ContentType 信息）
     * @param requestBody 原始请求体字符串
     * @return 转换后的 Map，如果请求体为空则返回空 Map
     */
    public Map<String, Object> adaptRequest(Workflow workflow, String requestBody) {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            return new HashMap<>();
        }

        WorkflowContentType contentType = workflow.getContentType();
        try {
            if (WorkflowContentType.XML.equals(contentType)) {
                return adaptXmlToMap(requestBody, workflow.getContentMetaInfo());
            } else {
                // 默认按 JSON 处理
                return adaptJsonToMap(requestBody);
            }
        } catch (Exception e) {
            log.error("请求协议适配失败，类型: {}, 错误: {}", contentType, e.getMessage(), e);
            throw new AgentConfigException(AgentErrorCode.CONTENT_TYPE_ADAPT_ERROR, e.getMessage(), e);
        }
    }

    /**
     * 将内部 Map 格式适配为响应体
     * 支持 JSON / XML 等多种协议
     *
     * @param workflow 工作流配置（包含 ContentType 信息）
     * @param responseData 内部响应数据 Map
     * @return 协议格式的响应字符串
     */
    public String adaptResponse(Workflow workflow, Map<String, Object> responseData) {
        WorkflowContentType contentType = workflow.getContentType();
        if (WorkflowContentType.JSON.equals(contentType)) {
            // 如果 Map 只有一个 key 且 key 为保留字 "data"，且该 value 是数组类型，直接返回数组的 JSON
            if (responseData.size() == 1 && responseData.containsKey("data")) {
                Object value = responseData.get("data");
                if (value instanceof Collection || (value != null && value.getClass().isArray())) {
                    return JsonUtils.toJsonString(value);
                }
            }
            return JsonUtils.toJsonString(responseData);
        } else if (WorkflowContentType.XML.equals(contentType)) {
            return XmlUtils.buildResponseXml(responseData, workflow.getContentMetaInfo());
        } else {
            throw new AgentConfigException(AgentErrorCode.CONTENT_TYPE_UNSUPPORTED, String.valueOf(contentType));
        }
    }

    /**
     * 将 JSON 字符串适配为 Map
     */
    private Map<String, Object> adaptJsonToMap(String json) {
        return JsonUtils.fromJsonStringToObjectMap(json);
    }

    /**
     * 将 SOAP XML 字符串适配为 Map
     * 处理 SOAP 信封，提取 CDATA 中的实际业务数据
     */
    private Map<String, Object> adaptXmlToMap(String xml, String contentMetaInfo) {
        Map<String, Object> metaMap = new HashMap<>();
        if (contentMetaInfo != null && !contentMetaInfo.isEmpty()) {
            metaMap = JsonUtils.parseMetaInfo(contentMetaInfo);
        }
        return XmlUtils.parseRequestXml(xml, metaMap);
    }
}
