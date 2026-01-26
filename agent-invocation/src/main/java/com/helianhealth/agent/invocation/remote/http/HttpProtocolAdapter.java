package com.helianhealth.agent.invocation.remote.http;

import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.common.utils.JsonUtils;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpProtocolAdapter implements InterfaceClientProxy {

    private final HttpRequestHandler httpRequestResolver;

    /**
     * 带参构造函数
     * HttpRequestHandler 通过 ExtensionFactory 自动注入（构造器注入）
     */
    public HttpProtocolAdapter(HttpRequestHandler httpRequestResolver) {
        this.httpRequestResolver = httpRequestResolver;
    }

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
            String method = (String) metaInfo.getOrDefault("method", "POST");
            Map<String, String> headers = (Map<String, String>) metaInfo.getOrDefault("headers", new HashMap<>());

            String requestBody = httpRequestResolver.resolveParamNodes(flowNode, params);
            String responseBody = httpRequestResolver.executeHttpRequest(url, method, headers, requestBody);

            return JsonUtils.toMap(responseBody);
        } catch (Exception e) {
            // 处理异常，可根据需要进行重试或降级处理
            throw new RuntimeException("HTTP远程调用失败: " + e.getMessage(), e);
        }
    }


}
