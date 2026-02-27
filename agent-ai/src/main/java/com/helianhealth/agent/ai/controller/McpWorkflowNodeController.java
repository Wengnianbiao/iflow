package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.dto.InterfaceWorkflowNodeView;
import com.helianhealth.agent.console.controller.request.WorkflowNodeCreateReq;
import com.helianhealth.agent.console.controller.request.WorkflowNodeUpdateReq;
import com.helianhealth.agent.console.service.InterfaceWorkflowNodeService;
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
 * MCP接口 - 工作流节点管理
 * 提供给AI使用的工作流节点CRUD接口
 */
@RestController
@RequestMapping("/v1/ai/mcp/workflow-node")
@Slf4j
@AllArgsConstructor
public class McpWorkflowNodeController {

    private final InterfaceWorkflowNodeService interfaceWorkflowNodeService;

    /**
     * 查询所有工作流节点（不分页）
     * 用于AI获取当前系统中的所有节点及其ID
     */
    @GetMapping("/list")
    public ResultData<List<InterfaceWorkflowNodeView>> listAllNodes() {
        try {
            log.info("MCP查询所有工作流节点");
            List<InterfaceWorkflowNodeView> nodes = interfaceWorkflowNodeService.getAllNodesWithoutPaged();
            return ResponseRenderUtils.render(nodes);
        } catch (Exception e) {
            log.error("MCP查询工作流节点列表失败", e);
            return ResponseRenderUtils.error("查询工作流节点列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据nodeId查询单个工作流节点
     */
    @GetMapping("/get/{nodeId}")
    public ResultData<WorkflowNode> getNode(@PathVariable Integer nodeId) {
        try {
            log.info("MCP查询工作流节点: nodeId={}", nodeId);
            WorkflowNode node = interfaceWorkflowNodeService.selectByNodeId(nodeId);
            if (node == null) {
                return ResponseRenderUtils.error("工作流节点不存在");
            }
            return ResponseRenderUtils.render(node);
        } catch (Exception e) {
            log.error("MCP查询工作流节点失败", e);
            return ResponseRenderUtils.error("查询工作流节点失败: " + e.getMessage());
        }
    }

    /**
     * 创建工作流节点
     */
    @PostMapping("/create")
    public ResultData<Integer> createWorkflowNode(@RequestBody WorkflowNodeCreateReq nodeRequest) {
        try {
            log.info("MCP创建工作流节点: {}", nodeRequest);
            int nodeId = interfaceWorkflowNodeService.save(nodeRequest);
            return ResponseRenderUtils.render(nodeId);
        } catch (Exception e) {
            log.error("MCP创建工作流节点失败", e);
            return ResponseRenderUtils.error("创建工作流节点失败: " + e.getMessage());
        }
    }

    /**
     * 更新工作流节点
     */
    @PostMapping("/update")
    public ResultData<Integer> updateWorkflowNode(@RequestBody WorkflowNodeUpdateReq updateRequest) {
        try {
            log.info("MCP更新工作流节点: {}", updateRequest);
            if (updateRequest.getNodeId() == null) {
                return ResponseRenderUtils.error("nodeId不能为空");
            }
            int result = interfaceWorkflowNodeService.update(updateRequest);
            return ResponseRenderUtils.render(result);
        } catch (Exception e) {
            log.error("MCP更新工作流节点失败", e);
            return ResponseRenderUtils.error("更新工作流节点失败: " + e.getMessage());
        }
    }

    /**
     * 删除工作流节点
     */
    @DeleteMapping("/delete/{nodeId}")
    public ResultData<String> deleteWorkflowNode(@PathVariable Integer nodeId) {
        try {
            log.info("MCP删除工作流节点: nodeId={}", nodeId);
            interfaceWorkflowNodeService.deleteByNodeId(nodeId);
            return ResponseRenderUtils.render("工作流节点删除成功");
        } catch (Exception e) {
            log.error("MCP删除工作流节点失败", e);
            return ResponseRenderUtils.error("删除工作流节点失败: " + e.getMessage());
        }
    }
}
