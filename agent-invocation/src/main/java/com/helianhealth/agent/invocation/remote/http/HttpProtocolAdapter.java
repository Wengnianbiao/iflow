package com.helianhealth.agent.invocation.remote.http;

import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.remote.AbstractProtocolAdapter;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP协议适配器
 */
public class HttpProtocolAdapter extends AbstractProtocolAdapter {

    private final HttpRequestHandler httpRequestHandler;

    /**
     * 带参构造函数
     * HttpRequestHandler 通过 ExtensionFactory 自动注入（构造器注入）
     */
    public HttpProtocolAdapter(HttpRequestHandler httpRequestHandler) {
        this.httpRequestHandler = httpRequestHandler;
    }

    @Override
    protected String resolveParams(WorkflowNode flowNode, List<ParamTreeNode> params) {
        // 检查节点类型是否为HTTP
        if (flowNode.getNodeType() != NodeType.HTTP) {
            throw new IllegalArgumentException("当前节点类型不是HTTP，无法进行HTTP远程调用");
        }
        return httpRequestHandler.resolveParamNodes(flowNode, params);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String executeInvoke(WorkflowNode flowNode, String resolvedParam) {
        Map<String, Object> metaInfo = JsonUtils.toMap(flowNode.getMetaInfo());
        if (metaInfo == null || !metaInfo.containsKey("url")) {
            throw new IllegalArgumentException("HTTP请求信息中缺少URL");
        }
        String url = (String) metaInfo.get("url");
        String method = (String) metaInfo.getOrDefault("method", "POST");
        Map<String, String> headers = (Map<String, String>) metaInfo.getOrDefault("headers", new HashMap<>());

        try {
            return httpRequestHandler.executeHttpRequest(url, method, headers, resolvedParam);
        } catch (Exception e) {
            throw new RuntimeException("HTTP远程调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    protected Map<String, Object> parseResponse(WorkflowNode flowNode, String rawResponse) {
        return JsonUtils.toMap(rawResponse);
    }
}
