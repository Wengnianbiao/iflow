package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowDO;
import com.helianhealth.agent.workflow.service.InterfaceWorkflowService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP接口 - 工作流管理
 * 提供给AI使用的工作流CRUD接口
 */
@RestController
@RequestMapping("/v1/ai/mcp/workflow")
@Slf4j
@AllArgsConstructor
public class McpWorkflowController {

    private final InterfaceWorkflowService interfaceWorkflowService;

    /**
     * 创建工作流
     */
    @PostMapping("/create")
    public ResultData<Integer> createWorkflow(@RequestBody InterfaceWorkflowDO workflow) {
        try {
            log.info("MCP创建工作流: {}", workflow);
            int flowId = interfaceWorkflowService.save(workflow);
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
    public ResultData<Integer> updateWorkflow(@RequestBody InterfaceWorkflowDO workflow) {
        try {
            log.info("MCP更新工作流: {}", workflow);
            if (workflow.getFlowId() == null) {
                return ResponseRenderUtils.error("flowId不能为空");
            }
            int result = interfaceWorkflowService.update(workflow);
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
            interfaceWorkflowService.deleteByFlowId(flowId);
            return ResponseRenderUtils.render("工作流删除成功");
        } catch (Exception e) {
            log.error("MCP删除工作流失败", e);
            return ResponseRenderUtils.error("删除工作流失败: " + e.getMessage());
        }
    }
}

