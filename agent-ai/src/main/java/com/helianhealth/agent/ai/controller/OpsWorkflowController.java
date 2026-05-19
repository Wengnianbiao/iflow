package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.ai.model.WorkflowCreateMcpRequest;
import com.helianhealth.agent.ai.model.WorkflowUpdateMcpRequest;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.enums.WorkflowContentType;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import com.helianhealth.agent.repository.service.WorkflowQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ops 接口 - 工作流管理
 *
 * <p>面向 AI / 内部运维的底层 REST 能力面，跟 {@code com.helianhealth.agent.ai.mcp.McpServerEndpoint}
 * 共用同一套 Service 层。MCP 协议入口 ({@code POST /mcp}) 内部直接调 Service，不绕这层 REST。</p>
 */
@RestController
@RequestMapping("/v1/ai/ops/workflow")
@Slf4j
public class OpsWorkflowController {

    private final WorkflowCommandService workflowCommandService;
    private final WorkflowQueryService workflowQueryService;

    public OpsWorkflowController(WorkflowCommandService workflowCommandService, WorkflowQueryService workflowQueryService) {
        this.workflowCommandService = workflowCommandService;
        this.workflowQueryService = workflowQueryService;
    }

    @GetMapping("/list")
    public ResultData<List<Workflow>> listAllWorkflows() {
        try {
            log.info("Ops 查询所有工作流");
            List<Workflow> workflows = workflowQueryService.selectAllWorkflows();
            return ResultData.ok(workflows);
        } catch (Exception e) {
            log.error("Ops 查询工作流列表失败", e);
            return ResultData.error("查询工作流列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/get/{flowId}")
    public ResultData<Workflow> getWorkflow(@PathVariable Integer flowId) {
        try {
            log.info("Ops 查询工作流: flowId={}", flowId);
            Workflow workflow = workflowQueryService.selectByFlowId(flowId);
            if (workflow == null) {
                return ResultData.error("工作流不存在");
            }
            return ResultData.ok(workflow);
        } catch (Exception e) {
            log.error("Ops 查询工作流失败", e);
            return ResultData.error("查询工作流失败: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResultData<Integer> createWorkflow(@RequestBody WorkflowCreateMcpRequest request) {
        try {
            log.info("Ops 创建工作流: {}", request);
            Workflow workflow = toWorkflowDomain(request);
            workflowCommandService.createWorkflow(workflow);
            Integer flowId = workflow.getFlowId();
            return ResultData.ok(flowId);
        } catch (Exception e) {
            log.error("Ops 创建工作流失败", e);
            return ResultData.error("创建工作流失败: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    public ResultData<Integer> updateWorkflow(@RequestBody WorkflowUpdateMcpRequest request) {
        try {
            log.info("Ops 更新工作流: {}", request);
            if (request.getFlowId() == null) {
                return ResultData.error("flowId不能为空");
            }
            Workflow workflow = toWorkflowDomain(request);
            int result = workflowCommandService.updateWorkflow(workflow);
            return ResultData.ok(result);
        } catch (Exception e) {
            log.error("Ops 更新工作流失败", e);
            return ResultData.error("更新工作流失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{flowId}")
    public ResultData<String> deleteWorkflow(@PathVariable Integer flowId) {
        try {
            log.info("Ops 删除工作流: flowId={}", flowId);
            workflowCommandService.deleteWorkflow(flowId);
            return ResultData.ok("工作流删除成功");
        } catch (Exception e) {
            log.error("Ops 删除工作流失败", e);
            return ResultData.error("删除工作流失败: " + e.getMessage());
        }
    }

    private Workflow toWorkflowDomain(WorkflowCreateMcpRequest request) {
        if (request == null) {
            return null;
        }
        return Workflow.builder()
                .flowName(request.getFlowName())
                .interfaceUri(request.getInterfaceUri())
                .routeExtension(request.getRouteExtension())
                .contentType(parseContentType(request.getContentType()))
                .contentMetaInfo(request.getContentMetaInfo())
                .firstFlowNodes(request.getFirstFlowNodes())
                .status(request.getStatus())
                .build();
    }

    private Workflow toWorkflowDomain(WorkflowUpdateMcpRequest request) {
        if (request == null) {
            return null;
        }
        return Workflow.builder()
                .flowId(request.getFlowId())
                .flowName(request.getFlowName())
                .interfaceUri(request.getInterfaceUri())
                .routeExtension(request.getRouteExtension())
                .contentType(parseContentType(request.getContentType()))
                .contentMetaInfo(request.getContentMetaInfo())
                .firstFlowNodes(request.getFirstFlowNodes())
                .status(request.getStatus())
                .build();
    }

    private WorkflowContentType parseContentType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return WorkflowContentType.valueOf(value.trim().toUpperCase());
    }
}
