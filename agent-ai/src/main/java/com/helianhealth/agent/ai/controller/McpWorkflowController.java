package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.ai.model.WorkflowCreateMcpRequest;
import com.helianhealth.agent.ai.model.WorkflowUpdateMcpRequest;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.enums.WorkflowContentType;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowDO;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import lombok.AllArgsConstructor;
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
 * MCP接口 - 工作流管理
 * 提供给AI使用的工作流CRUD接口
 */
@RestController
@RequestMapping("/v1/ai/mcp/workflow")
@Slf4j
@AllArgsConstructor
public class McpWorkflowController {

    private final WorkflowCommandService workflowCommandService;

    /**
     * 查询所有工作流（不分页）
     * 用于AI获取当前系统中的所有工作流及其ID
     */
    @GetMapping("/list")
    public ResultData<List<InterfaceWorkflowDO>> listAllWorkflows() {
        try {
            log.info("MCP查询所有工作流");
            List<InterfaceWorkflowDO> workflows = workflowCommandService.selectAllWorkflows();
            return ResponseRenderUtils.render(workflows);
        } catch (Exception e) {
            log.error("MCP查询工作流列表失败", e);
            return ResponseRenderUtils.error("查询工作流列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据flowId查询单个工作流
     */
    @GetMapping("/get/{flowId}")
    public ResultData<Workflow> getWorkflow(@PathVariable Integer flowId) {
        try {
            log.info("MCP查询工作流: flowId={}", flowId);
            InterfaceWorkflowDO workflowDO = workflowCommandService.selectWorkflowEntity(flowId);
            Workflow workflow = toWorkflow(workflowDO);
            if (workflow == null) {
                return ResponseRenderUtils.error("工作流不存在");
            }
            return ResponseRenderUtils.render(workflow);
        } catch (Exception e) {
            log.error("MCP查询工作流失败", e);
            return ResponseRenderUtils.error("查询工作流失败: " + e.getMessage());
        }
    }

    /**
     * 创建工作流
     */
    @PostMapping("/create")
    public ResultData<Integer> createWorkflow(@RequestBody WorkflowCreateMcpRequest request) {
        try {
            log.info("MCP创建工作流: {}", request);
            InterfaceWorkflowDO workflow = toWorkflowDO(request);
            workflowCommandService.createWorkflow(workflow);
            Integer flowId = workflow.getFlowId();
            return ResponseRenderUtils.render(flowId);
        } catch (Exception e) {
            log.error("MCP创建工作流失败", e);
            return ResponseRenderUtils.error("创建工作流失败: " + e.getMessage());
        }
    }

    /**
     * 更新工作流
     */
    @PostMapping("/update")
    public ResultData<Integer> updateWorkflow(@RequestBody WorkflowUpdateMcpRequest request) {
        try {
            log.info("MCP更新工作流: {}", request);
            if (request.getFlowId() == null) {
                return ResponseRenderUtils.error("flowId不能为空");
            }
            InterfaceWorkflowDO workflow = toWorkflowDO(request);
            int result = workflowCommandService.updateWorkflow(workflow);
            return ResponseRenderUtils.render(result);
        } catch (Exception e) {
            log.error("MCP更新工作流失败", e);
            return ResponseRenderUtils.error("更新工作流失败: " + e.getMessage());
        }
    }

    /**
     * 删除工作流
     */
    @DeleteMapping("/delete/{flowId}")
    public ResultData<String> deleteWorkflow(@PathVariable Integer flowId) {
        try {
            log.info("MCP删除工作流: flowId={}", flowId);
            workflowCommandService.deleteWorkflow(flowId);
            return ResponseRenderUtils.render("工作流删除成功");
        } catch (Exception e) {
            log.error("MCP删除工作流失败", e);
            return ResponseRenderUtils.error("删除工作流失败: " + e.getMessage());
        }
    }

    private Workflow toWorkflow(InterfaceWorkflowDO entity) {
        if (entity == null) {
            return null;
        }
        return Workflow.builder()
                .flowId(entity.getFlowId())
                .flowName(entity.getFlowName())
                .interfaceUri(entity.getInterfaceUri())
                .contentType(entity.getContentType())
                .contentMetaInfo(entity.getContentMetaInfo())
                .firstFlowNodes(entity.getFirstFlowNodes())
                .status(entity.getStatus())
                .build();
    }

    private InterfaceWorkflowDO toWorkflowDO(WorkflowCreateMcpRequest request) {
        if (request == null) {
            return null;
        }
        return InterfaceWorkflowDO.builder()
                .flowName(request.getFlowName())
                .interfaceUri(request.getInterfaceUri())
                .contentType(parseContentType(request.getContentType()))
                .contentMetaInfo(request.getContentMetaInfo())
                .firstFlowNodes(request.getFirstFlowNodes())
                .status(request.getStatus())
                .build();
    }

    private InterfaceWorkflowDO toWorkflowDO(WorkflowUpdateMcpRequest request) {
        if (request == null) {
            return null;
        }
        return InterfaceWorkflowDO.builder()
                .flowId(request.getFlowId())
                .flowName(request.getFlowName())
                .interfaceUri(request.getInterfaceUri())
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
