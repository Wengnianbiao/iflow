package com.helianhealth.agent.api.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.helianhealth.agent.api.JarvisRouteHandler;
import com.helianhealth.agent.api.model.request.JarvisRequest;
import com.helianhealth.agent.api.model.response.JarvisResultData;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.schedule.WorkflowScheduler;
import com.helianhealth.agent.schedule.builder.WorkFlowNodeBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jarvis 默认工作流编排实现。
 */
public class DefaultJarvisRouteHandler implements JarvisRouteHandler {

    private final WorkflowScheduler workflowScheduler;
    private final WorkFlowNodeBuilder workFlowNodeBuilder;

    public DefaultJarvisRouteHandler(WorkflowScheduler workflowScheduler,
                                     WorkFlowNodeBuilder workFlowNodeBuilder) {
        this.workflowScheduler = workflowScheduler;
        this.workFlowNodeBuilder = workFlowNodeBuilder;
    }

    @Override
    public JarvisResultData<Object> handle(Workflow workflow, JarvisRequest request) {
        Map<String, Object> businessData = resolveJarvisRequest(request);
        List<WorkflowNode> workflowNodes = workFlowNodeBuilder.build(workflow.getFirstFlowNodes());
        Map<String, Object> scheduleRsp = workflowScheduler.schedule(workflowNodes, businessData);
        return processJarvisRsp(scheduleRsp);
    }

    private Map<String, Object> resolveJarvisRequest(JarvisRequest request) {
        return request.getBusinessMethod().equals("GetItemResult")
                ? convertBeanToMap(request)
                : convertJsonObjectToMap(request.getData());
    }

    private JarvisResultData<Object> processJarvisRsp(Map<String, Object> flowNodeResponse) {
        if (flowNodeResponse.isEmpty()) {
            return JarvisResultData.ok();
        }
        Object code = flowNodeResponse.get("code");
        String message = (String) flowNodeResponse.get("message");
        Object rsp = flowNodeResponse.get("rsp");
        if ("200".equals(code)) {
            return JarvisResultData.ok(rsp);
        }
        return JarvisResultData.error(message);
    }

    private Map<String, Object> convertBeanToMap(Object bean) {
        Object parsed = JSON.parse(JSON.toJSONString(bean));
        if (!(parsed instanceof JSONObject)) {
            throw new IllegalArgumentException("Jarvis请求转换结果不是JSON对象");
        }
        return new LinkedHashMap<>((JSONObject) parsed);
    }

    private Map<String, Object> convertJsonObjectToMap(JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        return new LinkedHashMap<>(jsonObject);
    }
}
