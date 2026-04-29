package com.helianhealth.agent.invocation.remote.http;

import com.helianhealth.agent.common.base.MetaInfoKeyConstants;
import com.helianhealth.agent.common.exception.AgentConfigException;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.exception.AgentInvocationException;
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

    public HttpProtocolAdapter(HttpRequestHandler httpRequestHandler) {
        this.httpRequestHandler = httpRequestHandler;
    }

    @Override
    protected String resolveParam(WorkflowNode flowNode, List<ParamTreeNode> params) {
        try {
            return httpRequestHandler.resolveParamNodes(flowNode, params);
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentConfigException(AgentErrorCode.CONTENT_TYPE_ADAPT_ERROR, "HTTP参数解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String executeInvoke(WorkflowNode flowNode, String resolvedParam) {
        Map<String, Object> metaInfo = JsonUtils.parseMetaInfo(flowNode.getMetaInfo());
        if (metaInfo == null || !metaInfo.containsKey(MetaInfoKeyConstants.URL)) {
            throw new AgentConfigException(AgentErrorCode.HTTP_CONFIG_MISSING, "HTTP请求信息中缺少URL");
        }
        String url = (String) metaInfo.get(MetaInfoKeyConstants.URL);
        String method = (String) metaInfo.getOrDefault(MetaInfoKeyConstants.HTTP_METHOD, "POST");
        Map<String, String> headers = (Map<String, String>) metaInfo.getOrDefault(MetaInfoKeyConstants.HEADERS, new HashMap<>());

        try {
            return httpRequestHandler.executeHttpRequest(url, method, headers, resolvedParam);
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentInvocationException(AgentErrorCode.HTTP_CALL_FAILED, e.getMessage(), e);
        }
    }

    @Override
    protected Map<String, Object> parseResponse(WorkflowNode flowNode, String rawResponse) {
        try {
            return JsonUtils.parseObjectMap(rawResponse);
        } catch (Exception e) {
            throw new AgentInvocationException(AgentErrorCode.REMOTE_RESPONSE_PARSE_ERROR, "HTTP响应解析失败: " + e.getMessage(), e);
        }
    }
}
