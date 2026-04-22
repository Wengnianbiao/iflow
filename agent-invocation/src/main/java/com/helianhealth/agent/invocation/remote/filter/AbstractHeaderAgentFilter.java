package com.helianhealth.agent.invocation.remote.filter;

import com.helianhealth.agent.common.base.ExtensionFilterConstants;
import com.helianhealth.agent.common.base.MetaInfoKeyConstants;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 请求头过滤器模板
 */
public abstract class AbstractHeaderAgentFilter implements AgentFilter {

    private static final Logger log = LoggerFactory.getLogger(AbstractHeaderAgentFilter.class);

    @Override
    public final Map<String, Object> doInvoke(InterfaceClientProxy next, WorkflowNode flowNode, List<ParamTreeNode> params) {
        Map<String, String> extensionParam = flowNode.getExtensionParam();
        if (extensionParam == null) {
            return next.doInvoke(flowNode, params);
        }

        String headerConfig = extensionParam.get(ExtensionFilterConstants.EXTENSION_PARAM_KEY_HEADER);
        if (headerConfig == null || headerConfig.trim().isEmpty()) {
            return next.doInvoke(flowNode, params);
        }

        if (!"true".equalsIgnoreCase(headerConfig.trim())) {
            return next.doInvoke(flowNode, params);
        }

        try {
            Map<String, Object> metaInfo = JsonUtils.parseMetaInfo(flowNode.getMetaInfo());
            Map<String, String> headers = extractHeaders(metaInfo.get(MetaInfoKeyConstants.HEADERS));
            doFilter(headers);
            metaInfo.put(MetaInfoKeyConstants.HEADERS, headers);
            flowNode.setMetaInfo(JsonUtils.toJsonString(metaInfo));
        } catch (Exception e) {
            log.error("HeaderAgentFilter处理失败, nodeId={}", flowNode.getNodeId(), e);
        }

        return next.doInvoke(flowNode, params);
    }

    protected abstract void doFilter(Map<String, String> headers);

    private Map<String, String> extractHeaders(Object headerObject) {
        Map<String, String> headers = new HashMap<>();
        if (!(headerObject instanceof Map)) {
            return headers;
        }

        Map<?, ?> source = (Map<?, ?>) headerObject;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            headers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return headers;
    }
}
