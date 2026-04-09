package com.helianhealth.agent.api;

import com.helianhealth.agent.api.adapter.ContentTypeAdapter;
import com.helianhealth.agent.api.model.request.JarvisRequest;
import com.helianhealth.agent.api.model.response.JarvisResultData;
import com.helianhealth.agent.common.exception.AgentConfigException;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.WorkflowQueryService;
import com.helianhealth.agent.schedule.WorkflowScheduler;
import com.helianhealth.agent.schedule.builder.WorkFlowNodeBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工作流节点分发器
 * Dispatcher负责去调度工作流节节点之间流转
 */
@Component
@Slf4j
@AllArgsConstructor
public class WorkFlowOrchestrator {

    private final WorkflowQueryService workflowService;
    private final WorkflowScheduler workflowScheduler;
    private final WorkFlowNodeBuilder workFlowNodeBuilder;
    private final ContentTypeAdapter contentTypeAdapter;

    /**
     * 适配jarvis的参数解析调度
     */
    public JarvisResultData<Object> jarvisDispatch(JarvisRequest request) {
        try {
            Optional<Workflow> workflowOptional = workflowService.findByInterfaceUri(request.getBusinessMethod());
            if (workflowOptional.isPresent()) {
                // 兼容jarvis的API调用入参,理论上jarvis应当统一入参格式
                Map<String, Object> businessData = request.getBusinessMethod().equals("GetItemResult") ?
                        JsonUtils.toMap(request) : JsonUtils.toMap(request.getData());
                List<WorkflowNode> workflowNodes = workFlowNodeBuilder.build(workflowOptional.get()
                        .getFirstFlowNodes());
                Map<String, Object> scheduleRsp = workflowScheduler.schedule(workflowNodes,
                        businessData);
                return processJarvisRsp(scheduleRsp);
            } else {
                // 对于没有实现jarvis的接口,默认都返回成功
                log.warn("jarvis接口未匹配工作流, businessMethod={}", request.getBusinessMethod());
                return JarvisResultData.ok();
            }
        } catch (AgentException e) {
            log.error("jarvis调度异常, businessMethod={}: {}", request.getBusinessMethod(), e.getMessage(), e);
            return JarvisResultData.error(request.getBusinessMethod() + " | " + e.getMessage());
        } catch (Exception e) {
            log.error("jarvis调度未知异常, businessMethod={}", request.getBusinessMethod(), e);
            return JarvisResultData.error(request.getBusinessMethod() + " | " + extractRootCauseMessage(e));
        }
    }

    private JarvisResultData<Object> processJarvisRsp(Map<String, Object> flowNodeResponse) {
        if (flowNodeResponse.isEmpty()) {
            return JarvisResultData.ok();
        }
        Object code = flowNodeResponse.get("code");
        String message = (String) flowNodeResponse.get("message");
        Object rsp = flowNodeResponse.get("rsp");

        // 只有200才是真的成功
        if ("200".equals(code)) {
            return JarvisResultData.ok(rsp);
        } else {
            return JarvisResultData.error(message);
        }
    }

    /**
     * open api 调度
     */
    public String dispatch(String request, String interfaceUri) {
        try {
            // 1、根据调用URI获取对应的工作流
            Optional<Workflow> workflowOptional = workflowService.findByInterfaceUri(interfaceUri);
            if (workflowOptional.isPresent()) {
                Workflow workflow = workflowOptional.get();
                // 2、参数预处理,根据contentType转化为Map
                Map<String, Object> businessData = contentTypeAdapter.adaptRequest(workflow, request);
                List<WorkflowNode> workflowNodes = workFlowNodeBuilder.build(workflowOptional.get()
                        .getFirstFlowNodes());
                // 3、调度工作流
                Map<String, Object> scheduleRsp = workflowScheduler.schedule(workflowNodes,
                        businessData);

                // 4、根据工作流contentType构建响应
                return contentTypeAdapter.adaptResponse(workflow, scheduleRsp);
            } else {
                log.error("open api接口未匹配工作流, uri={}", interfaceUri);
                throw new AgentConfigException(AgentErrorCode.METHOD_NOT_SUPPORT, "uri=" + interfaceUri);
            }
        } catch (AgentException e) {
            log.error("open api调度异常, uri={}: {}", interfaceUri, e.getMessage(), e);
            return buildErrorResponse(e.getErrorCode().getCode(), e.getErrorCode().getMessage(), e.getDetail());
        } catch (Exception e) {
            log.error("open api调度未知异常, uri={}", interfaceUri, e);
            return buildErrorResponse(AgentErrorCode.INTERNAL_ERROR.getCode(),
                    AgentErrorCode.INTERNAL_ERROR.getMessage(), extractRootCauseMessage(e));
        }
    }

    private String buildErrorResponse(int code, String message, String detail) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (detail != null && !detail.isEmpty()) {
            error.put("detail", detail);
        }
        return JsonUtils.toJsonString(error);
    }

    /**
     * 提取异常链中第一个有业务含义的消息
     */
    private String extractRootCauseMessage(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isEmpty()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return e.getClass().getName();
    }
}
