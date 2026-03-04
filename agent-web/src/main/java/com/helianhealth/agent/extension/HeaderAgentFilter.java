package com.helianhealth.agent.extension;

import com.helianhealth.agent.common.base.ExtensionConstants;
import com.helianhealth.agent.common.extension.AgentActivate;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.filter.AgentFilter;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Header 处理过滤器
 * 根据节点的 extensionParam 中的配置动态增强 HTTP 请求头
 */
@AgentActivate(order = 50)
@Slf4j
public class HeaderAgentFilter implements AgentFilter {

    private static final String META_INFO_HEADERS_KEY = "headers";

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> doInvoke(InterfaceClientProxy next, WorkflowNode flowNode, List<ParamTreeNode> params) {
        Map<String, String> extensionParam = flowNode.getExtensionParam();
        if (extensionParam == null || !extensionParam.containsKey(ExtensionConstants.EXTENSION_PARAM_KEY_HEADER)) {
            return next.doInvoke(flowNode, params);
        }

        String headerConfigJson = extensionParam.get(ExtensionConstants.EXTENSION_PARAM_KEY_HEADER);
        if (headerConfigJson == null || headerConfigJson.isEmpty()) {
            return next.doInvoke(flowNode, params);
        }

        try {
            // 1. 判断是否开启 Header 处理
            boolean enabled = "true".equalsIgnoreCase(headerConfigJson.trim());
            if (!enabled) {
                return next.doInvoke(flowNode, params);
            }

            // 2. 解析 metaInfo 并合并 headers
            Map<String, Object> metaInfo = JsonUtils.toMap(flowNode.getMetaInfo());
            Map<String, String> headers = (Map<String, String>) metaInfo.get(META_INFO_HEADERS_KEY);
            if (headers == null) {
                headers = new HashMap<>();
            }

            // 3. 在这里添加自定义 Header（按需DIY）
            headers.put("X-Trace-Id", "123344543534");

            metaInfo.put(META_INFO_HEADERS_KEY, headers);

            // 4. 回写 metaInfo 到节点
            flowNode.setMetaInfo(JsonUtils.toJsonString(metaInfo));
        } catch (Exception e) {
            log.error("HeaderAgentFilter 处理请求头失败, nodeId={}", flowNode.getNodeId(), e);
        }

        // 4. 继续调用链
        return next.doInvoke(flowNode, params);
    }
}
