package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.workflow.controller.request.WorkflowNodeCreateReq;
import com.helianhealth.agent.workflow.controller.request.WorkflowNodeUpdateReq;
import com.helianhealth.agent.workflow.service.InterfaceWorkflowNodeService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 批量删除工作流节点
     */
    @DeleteMapping("/batch-delete")
    public ResultData<String> batchDeleteWorkflowNodes(@RequestBody List<Integer> nodeIds) {
        try {
            log.info("MCP批量删除工作流节点: nodeIds={}", nodeIds);
            int deletedCount = interfaceWorkflowNodeService.batchDeleteByNodeIds(nodeIds);
            return ResponseRenderUtils.render("成功删除" + deletedCount + "个节点，共尝试删除" + nodeIds.size() + "个节点");
        } catch (Exception e) {
            log.error("MCP批量删除工作流节点失败", e);
            return ResponseRenderUtils.error("批量删除工作流节点失败: " + e.getMessage());
        }
    }

    /**
     * 批量导入工作流节点
     */
    @PostMapping("/batch-import")
    public ResultData<String> batchImportWorkflowNodes(@RequestParam("file") MultipartFile file) {
        try {
            log.info("MCP批量导入工作流节点: 文件名={}", file.getOriginalFilename());
            interfaceWorkflowNodeService.batchImportNodeConfig(file);
            return ResponseRenderUtils.render("批量导入工作流节点成功");
        } catch (Exception e) {
            log.error("MCP批量导入工作流节点失败", e);
            return ResponseRenderUtils.error("批量导入工作流节点失败: " + e.getMessage());
        }
    }
}