package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.ai.model.WorkflowNodeCreateMcpRequest;
import com.helianhealth.agent.ai.model.WorkflowNodeUpdateMcpRequest;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.dto.InterfaceWorkflowNodeView;
import com.helianhealth.agent.repository.service.WorkflowNodeCommandService;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
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

    private final WorkflowNodeCommandService workflowNodeCommandService;
    private final WorkflowNodeQueryService workflowNodeQueryService;

    /**
     * 查询所有工作流节点（不分页）
     * 用于AI获取当前系统中的所有节点及其ID
     */
    @GetMapping("/list")
    public ResultData<List<InterfaceWorkflowNodeView>> listAllNodes() {
        try {
            log.info("MCP查询所有工作流节点");
            List<InterfaceWorkflowNodeView> views = workflowNodeQueryService.selectAllNodes(null);
            return ResultData.ok(views);
        } catch (Exception e) {
            log.error("MCP查询工作流节点列表失败", e);
            return ResultData.error("查询工作流节点列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据nodeId查询单个工作流节点
     */
    @GetMapping("/get/{nodeId}")
    public ResultData<WorkflowNode> getNode(@PathVariable Integer nodeId) {
        try {
            log.info("MCP查询工作流节点: nodeId={}", nodeId);
            WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
            if (node == null) {
                return ResultData.error("工作流节点不存在");
            }
            return ResultData.ok(node);
        } catch (Exception e) {
            log.error("MCP查询工作流节点失败", e);
            return ResultData.error("查询工作流节点失败: " + e.getMessage());
        }
    }

    /**
     * 创建工作流节点
     */
    @PostMapping("/create")
    public ResultData<Integer> createWorkflowNode(@RequestBody WorkflowNodeCreateMcpRequest nodeRequest) {
        try {
            log.info("MCP创建工作流节点: {}", nodeRequest);
            WorkflowNode node = toNodeDomain(nodeRequest);
            workflowNodeCommandService.createNode(node);
            int nodeId = node.getNodeId();
            return ResultData.ok(nodeId);
        } catch (Exception e) {
            log.error("MCP创建工作流节点失败", e);
            return ResultData.error("创建工作流节点失败: " + e.getMessage());
        }
    }

    /**
     * 更新工作流节点
     */
    @PostMapping("/update")
    public ResultData<Integer> updateWorkflowNode(@RequestBody WorkflowNodeUpdateMcpRequest updateRequest) {
        try {
            log.info("MCP更新工作流节点: {}", updateRequest);
            if (updateRequest.getNodeId() == null) {
                return ResultData.error("nodeId不能为空");
            }
            WorkflowNode node = toNodeDomain(updateRequest);
            int result = workflowNodeCommandService.updateNodeSelective(node);
            return ResultData.ok(result);
        } catch (Exception e) {
            log.error("MCP更新工作流节点失败", e);
            return ResultData.error("更新工作流节点失败: " + e.getMessage());
        }
    }

    /**
     * 删除工作流节点
     */
    @DeleteMapping("/delete/{nodeId}")
    public ResultData<String> deleteWorkflowNode(@PathVariable Integer nodeId) {
        try {
            log.info("MCP删除工作流节点: nodeId={}", nodeId);
            workflowNodeCommandService.deleteNode(nodeId);
            return ResultData.ok("工作流节点删除成功");
        } catch (Exception e) {
            log.error("MCP删除工作流节点失败", e);
            return ResultData.error("删除工作流节点失败: " + e.getMessage());
        }
    }

    private WorkflowNode toNodeDomain(WorkflowNodeCreateMcpRequest request) {
        if (request == null) {
            return null;
        }
        return WorkflowNode.builder()
                .nodeName(request.getNodeName())
                .flowId(request.getFlowId())
                .paramFilterExpr(request.getParamFilterExpr())
                .nodeType(parseNodeType(request.getNodeType()))
                .metaInfo(request.getMetaInfo())
                .keywordField(request.getKeywordField())
                .scheduleExpr(request.getScheduleExpr())
                .splitField(request.getSplitField())
                .remoteExtension(request.getRemoteExtension())
                .extensionParam(request.getExtensionParam() != null ? JsonUtils.parseStringMap(request.getExtensionParam()) : null)
                .scheduleParamSourceType(parseScheduleParamSourceType(request.getScheduleParamSourceType()))
                .build();
    }

    private WorkflowNode toNodeDomain(WorkflowNodeUpdateMcpRequest request) {
        if (request == null) {
            return null;
        }
        return WorkflowNode.builder()
                .nodeId(request.getNodeId())
                .nodeName(request.getNodeName())
                .flowId(request.getFlowId())
                .paramFilterExpr(request.getParamFilterExpr())
                .nodeType(parseNodeType(request.getNodeType()))
                .metaInfo(request.getMetaInfo())
                .keywordField(request.getKeywordField())
                .splitField(request.getSplitField())
                .remoteExtension(request.getRemoteExtension())
                .extensionParam(request.getExtensionParam() != null ? JsonUtils.parseStringMap(request.getExtensionParam()) : null)
                .scheduleExpr(request.getScheduleExpr())
                .scheduleParamSourceType(parseScheduleParamSourceType(request.getScheduleParamSourceType()))
                .build();
    }

    private ScheduleParamSourceType parseScheduleParamSourceType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return ScheduleParamSourceType.valueOf(value.trim().toUpperCase());
    }

    private NodeType parseNodeType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return NodeType.valueOf(value.trim().toUpperCase());
    }
}
