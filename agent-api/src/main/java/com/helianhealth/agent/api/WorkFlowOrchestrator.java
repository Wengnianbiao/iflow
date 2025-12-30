package com.helianhealth.agent.api;

import com.helianhealth.agent.api.model.request.JarvisRequest;
import com.helianhealth.agent.api.adapter.ContentTypeAdapter;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.exception.WorkflowBusinessException;
import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowDO;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.schedule.WorkflowScheduler;
import com.helianhealth.agent.schedule.builder.WorkFlowNodeBuilder;
import com.helianhealth.agent.workflow.service.InterfaceWorkflowService;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    private final InterfaceWorkflowService workflowService;
    private final WorkflowScheduler workflowScheduler;
    private final WorkFlowNodeBuilder workFlowNodeBuilder;
    private final ContentTypeAdapter contentTypeAdapter;

    /**
     * 适配jarvis的参数解析调度
     */
    public ResultData<Object> jarvisDispatch(JarvisRequest request) {
        try {
            Optional<InterfaceWorkflowDO> workflowOptional = workflowService.findByInterfaceUri(request.getBusinessMethod());
            if (workflowOptional.isPresent()) {
                // 兼容jarvis的API调用入参,理论上jarvis应当统一入参格式
                Map<String, Object> businessData = request.getBusinessMethod().equals("GetItemResult") ?
                        JsonUtils.toMap(request) : JsonUtils.toMap(request.getData());
                List<InterfaceWorkflowNode> workflowNodes = workFlowNodeBuilder.build(workflowOptional.get()
                        .getFirstFlowNodes());
                Map<String, Object> scheduleRsp = workflowScheduler.schedule(workflowNodes,
                        businessData);
                return processJarvisRsp(scheduleRsp);
            } else {
                log.error(WorkflowBusinessException.METHOD_NOT_SUPPORT.getMessage());
                // 对于没有实现jarvis的接口,直接返回成功
                return ResultData.ok();
            }
        } catch (Exception e) {
            log.error("dispatch request error:", e);
            return ResultData.fail();
        }
    }

    private ResultData<Object> processJarvisRsp(Map<String, Object> flowNodeResponse) {
        Object code = flowNodeResponse.get("code");
        String message = (String) flowNodeResponse.get("message");
        Object rsp = flowNodeResponse.get("rsp");

        // 200或者空(没有数据而已)都表示调用成功
        if ("200".equals(code) || code == null) {
            return ResponseRenderUtils.render(rsp);
        } else {
            return ResponseRenderUtils.error(message);
        }
    }

    /**
     * open api 调度
     */
    public String dispatch(String request, String interfaceUri) {
        try {
            // 1、根据调用URI获取对应的工作流
            Optional<InterfaceWorkflowDO> workflowOptional = workflowService.findByInterfaceUri(interfaceUri);
            if (workflowOptional.isPresent()) {
                InterfaceWorkflowDO workflow = workflowOptional.get();
                // 2、参数预处理,根据contentType转化为Map
                Map<String, Object> businessData = contentTypeAdapter.adaptRequest(workflow, request);
                List<InterfaceWorkflowNode> workflowNodes = workFlowNodeBuilder.build(workflowOptional.get()
                        .getFirstFlowNodes());
                // 3、调度工作流
                Map<String, Object> scheduleRsp = workflowScheduler.schedule(workflowNodes,
                        businessData);

                // 4、根据工作流contentType构建响应
                return contentTypeAdapter.adaptResponse(workflow, scheduleRsp);
            } else {
                log.error(WorkflowBusinessException.METHOD_NOT_SUPPORT.getMessage());
                throw WorkflowBusinessException.METHOD_NOT_SUPPORT.toException();
            }
        } catch (Exception e) {
            log.error("dispatch request error:", e);
            return null;
        }
    }
}
