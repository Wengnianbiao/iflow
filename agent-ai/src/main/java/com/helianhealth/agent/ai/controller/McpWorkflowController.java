package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.console.model.domain.InterfaceWorkflowDO;
import com.helianhealth.agent.console.service.InterfaceWorkflowService;
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

    private final InterfaceWorkflowService interfaceWorkflowService;

    /**
     * 查询所有工作流（不分页）
     * 用于AI获取当前系统中的所有工作流及其ID
     */
    @GetMapping("/list")
    public ResultData<List<InterfaceWorkflowDO>> listAllWorkflows() {
        try {
            log.info("MCP查询所有工作流");
            List<InterfaceWorkflowDO> workflows = interfaceWorkflowService.getAllWorkflowsWithoutPaged();
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
    public ResultData<InterfaceWorkflowDO> getWorkflow(@PathVariable Integer flowId) {
        try {
            log.info("MCP查询工作流: flowId={}", flowId);
            InterfaceWorkflowDO workflow = interfaceWorkflowService.selectByFlowId(flowId);
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

