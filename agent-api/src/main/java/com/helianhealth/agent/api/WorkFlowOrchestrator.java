package com.helianhealth.agent.api;

import com.helianhealth.agent.api.model.request.JarvisRequest;
import com.helianhealth.agent.api.model.response.JarvisResultData;
import com.helianhealth.agent.common.exception.AgentConfigException;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.service.WorkflowQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 工作流节点分发器
 * Dispatcher负责去调度工作流节节点之间流转
 */
@Component
@Slf4j
public class WorkFlowOrchestrator {

    private final WorkflowQueryService workflowService;
    private final OpenApiRouteHandler openApiRouteHandler;
    private final JarvisRouteHandler jarvisRouteHandler;

    public WorkFlowOrchestrator(WorkflowQueryService workflowService) {
        this.workflowService = workflowService;
        this.openApiRouteHandler = AgentExtensionLoader.getExtensionLoader(OpenApiRouteHandler.class)
                .getAdaptiveExtension();
        this.jarvisRouteHandler = AgentExtensionLoader.getExtensionLoader(JarvisRouteHandler.class)
                .getAdaptiveExtension();
    }

    /**
     * 适配jarvis的参数解析调度
     */
    public JarvisResultData<Object> jarvisDispatch(JarvisRequest request) {
        try {
            Optional<Workflow> workflowOptional = workflowService.findByInterfaceUri(request.getBusinessMethod());
            if (workflowOptional.isPresent()) {
                return jarvisRouteHandler.handle(workflowOptional.get(), request);
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

    /**
     * open api 调度
     */
    public String dispatch(String request, String interfaceUri) {
        try {
            // 1、根据调用URI获取对应的工作流
            Optional<Workflow> workflowOptional = workflowService.findByInterfaceUri(interfaceUri);
            if (workflowOptional.isPresent()) {
                return openApiRouteHandler.handle(workflowOptional.get(), request);
            } else {
                log.error("open api接口未匹配工作流, uri={}", interfaceUri);
                throw new AgentConfigException(AgentErrorCode.METHOD_NOT_SUPPORT, "uri=" + interfaceUri);
            }
        } catch (AgentException e) {
            log.error("open api调度异常, uri={}: {}", interfaceUri, e.getMessage(), e);
            return buildErrorResponse(e.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            log.error("open api调度未知异常, uri={}", interfaceUri, e);
            return buildErrorResponse(AgentErrorCode.INTERNAL_ERROR.getCode(),
                    extractRootCauseMessage(e));
        }
    }

    private String buildErrorResponse(int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
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
