/**
 * SPI 扩展在线编辑器的 Java 代码模板
 */
export const SPI_EXTENSION_TEMPLATES = {
  interfaceRemoteInvoker: `package com.helianhealth.agent.extension;

import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CustomInvoker implements InterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(Invocation invocation, WorkflowNode node) {
        // TODO: 实现自定义调用逻辑
        log.info("自定义调用: {}", node.getNodeName());
        return new HashMap<>();
    }
}`,
  workflowRouteHandler: `package com.helianhealth.agent.extension;

import com.helianhealth.agent.api.OpenApiRouteHandler;
import com.helianhealth.agent.repository.model.domain.Workflow;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowRouteHandler implements OpenApiRouteHandler {

    @Override
    public String handle(Workflow workflow, String requestBody, String interfaceUri) {
        log.info("workflow open api handle, uri={}, body={}", interfaceUri, requestBody);
        return requestBody;
    }
}`,
  jarvisRouteHandler: `package com.helianhealth.agent.extension;

import com.helianhealth.agent.api.JarvisRouteHandler;
import com.helianhealth.agent.api.model.request.JarvisRequest;
import com.helianhealth.agent.api.model.response.JarvisResultData;
import com.helianhealth.agent.repository.model.domain.Workflow;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JarvisWorkflowRouteHandler implements JarvisRouteHandler {

    @Override
    public JarvisResultData<Object> handle(Workflow workflow, JarvisRequest request) {
        log.info("jarvis workflow handle, businessMethod={}", request.getBusinessMethod());
        return JarvisResultData.ok(request);
    }
}`,
  agentFilter: `package com.helianhealth.agent.extension;

import com.helianhealth.agent.invocation.remote.filter.AgentFilter;
import com.helianhealth.agent.invocation.remote.filter.AgentFilterChain;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class CustomFilter implements AgentFilter {

    @Override
    public Map<String, Object> invoke(Invocation invocation, WorkflowNode node, AgentFilterChain chain) {
        // TODO: 实现请求预处理逻辑
        log.info("Filter 前置处理: {}", node.getNodeName());

        // 调用下一个过滤器或实际调用
        Map<String, Object> result = chain.doFilter(invocation, node);

        // TODO: 实现响应后处理逻辑
        log.info("Filter 后置处理");
        return result;
    }
}`
};
