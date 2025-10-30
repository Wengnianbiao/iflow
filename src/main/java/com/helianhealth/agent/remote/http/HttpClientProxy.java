package com.helianhealth.agent.remote.http;

import com.helianhealth.agent.enums.NodeType;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.InterfaceClientProxy;
import com.helianhealth.agent.utils.JsonUtils;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpClientProxy implements InterfaceClientProxy {

    @Setter
    private HttpRequestHandler httpRequestHandler_findBySpring;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> doInvoke(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {
        try {
            // 1. 检查节点类型是否为HTTP
            if (flowNode.getNodeType() != NodeType.HTTP) {
                throw new IllegalArgumentException("当前节点类型不是HTTP，无法进行HTTP远程调用");
            }

            // 2. 解析元数据（包含HTTP请求信息）
            Map<String, Object> metaInfo = JsonUtils.toMap(flowNode.getMetaInfo());
            if (metaInfo == null || !metaInfo.containsKey("url")) {
                throw new IllegalArgumentException("HTTP请求信息中缺少URL");
            }
            String url = (String) metaInfo.get("url");
            String method = (String) metaInfo.getOrDefault("method", "GET");
            Map<String, String> headers = (Map<String, String>) metaInfo.getOrDefault("headers", new HashMap<>());

            String requestBody = httpRequestHandler_findBySpring.resolveParamNodes(flowNode, params);
            String responseBody = httpRequestHandler_findBySpring.executeHttpRequest(url, method, headers, requestBody);

            return JsonUtils.toMap(responseBody);
        } catch (Exception e) {
            // 处理异常，可根据需要进行重试或降级处理
            throw new RuntimeException("HTTP远程调用失败: " + e.getMessage(), e);
        }
    }


}