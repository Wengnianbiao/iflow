package com.helianhealth.agent.ai.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.ai.model.WorkflowNodeMcpView;
import com.helianhealth.agent.ai.model.WorkflowNodeCreateMcpRequest;
import com.helianhealth.agent.ai.model.WorkflowNodeUpdateMcpRequest;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ops 接口 - 工作流节点管理（底层 REST 能力面）。
 */
@RestController
@RequestMapping("/v1/ai/ops/workflow-node")
@Slf4j
@AllArgsConstructor
public class OpsWorkflowNodeController {

    private final WorkflowNodeCommandService workflowNodeCommandService;
    private final WorkflowNodeQueryService workflowNodeQueryService;

    @GetMapping("/list")
    public ResultData<List<WorkflowNodeMcpView>> listAllNodes() {
        try {
            log.info("Ops 查询所有工作流节点");
            List<WorkflowNodeMcpView> views = workflowNodeQueryService.selectAllNodes(null).stream()
                    .map(this::toNodeView)
                    .collect(Collectors.toList());
            return ResultData.ok(views);
        } catch (Exception e) {
            log.error("Ops 查询工作流节点列表失败", e);
            return ResultData.error("查询工作流节点列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/get/{nodeId}")
    public ResultData<WorkflowNode> getNode(@PathVariable Integer nodeId) {
        try {
            log.info("Ops 查询工作流节点: nodeId={}", nodeId);
            WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
            if (node == null) {
                return ResultData.error("工作流节点不存在");
            }
            return ResultData.ok(node);
        } catch (Exception e) {
            log.error("Ops 查询工作流节点失败", e);
            return ResultData.error("查询工作流节点失败: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResultData<Integer> createWorkflowNode(@RequestBody WorkflowNodeCreateMcpRequest nodeRequest) {
        try {
            log.info("Ops 创建工作流节点: {}", nodeRequest);
            WorkflowNode node = toNodeDomain(nodeRequest);
            workflowNodeCommandService.createNode(node);
            int nodeId = node.getNodeId();
            return ResultData.ok(nodeId);
        } catch (Exception e) {
            log.error("Ops 创建工作流节点失败", e);
            return ResultData.error("创建工作流节点失败: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    public ResultData<Integer> updateWorkflowNode(@RequestBody WorkflowNodeUpdateMcpRequest updateRequest) {
        try {
            log.info("Ops 更新工作流节点: {}", updateRequest);
            if (updateRequest.getNodeId() == null) {
                return ResultData.error("nodeId不能为空");
            }
            WorkflowNode node = toNodeDomain(updateRequest);
            int result = workflowNodeCommandService.updateNodeSelective(node);
            return ResultData.ok(result);
        } catch (Exception e) {
            log.error("Ops 更新工作流节点失败", e);
            return ResultData.error("更新工作流节点失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{nodeId}")
    public ResultData<String> deleteWorkflowNode(@PathVariable Integer nodeId) {
        try {
            log.info("Ops 删除工作流节点: nodeId={}", nodeId);
            workflowNodeCommandService.deleteNode(nodeId);
            return ResultData.ok("工作流节点删除成功");
        } catch (Exception e) {
            log.error("Ops 删除工作流节点失败", e);
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
                .extensionParam(parseExtensionParam(request.getExtensionParam()))
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
                .extensionParam(parseExtensionParam(request.getExtensionParam()))
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

    private Map<String, String> parseExtensionParam(String extensionParam) {
        if (extensionParam == null || extensionParam.trim().isEmpty()) {
            return null;
        }
        return JSON.parseObject(extensionParam, new TypeReference<Map<String, String>>() {});
    }

    private WorkflowNodeMcpView toNodeView(WorkflowNode node) {
        WorkflowNodeMcpView view = new WorkflowNodeMcpView();
        view.setNodeId(node.getNodeId());
        view.setNodeName(node.getNodeName());
        view.setFlowId(node.getFlowId());
        view.setParamFilterExpr(node.getParamFilterExpr());
        view.setNodeType(node.getNodeType());
        view.setMetaInfo(node.getMetaInfo());
        view.setSplitField(node.getSplitField());
        view.setScheduleExpr(node.getScheduleExpr());
        view.setScheduleParamSourceType(node.getScheduleParamSourceType());
        view.setKeywordField(node.getKeywordField());
        view.setRemoteExtension(node.getRemoteExtension());
        view.setExtensionParam(node.getExtensionParam() == null ? null : JsonUtils.toJsonString(node.getExtensionParam()));
        return view;
    }
}
