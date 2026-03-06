package com.helianhealth.agent.console.controller;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.dto.InterfaceWorkflowNodeView;
import com.helianhealth.agent.console.controller.reponse.NodeConfigExportRsp;
import com.helianhealth.agent.console.controller.reponse.NodeParamConfigExportRsp;
import com.helianhealth.agent.console.controller.request.WorkflowNodeCreateReq;
import com.helianhealth.agent.console.controller.request.WorkflowNodeScheduleUpdateReq;
import com.helianhealth.agent.console.controller.request.WorkflowNodeUpdateReq;
import com.helianhealth.agent.console.service.InterfaceWorkflowNodeService;
import com.helianhealth.agent.console.service.NodeParamConfigService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 工作流配置控制器
 */
@RestController
@RequestMapping("/v1/console/node")
@Slf4j
@AllArgsConstructor
public class WorkflowNodeController {

    private final InterfaceWorkflowNodeService interfaceFlowNodeService;
    private final NodeParamConfigService nodeParamConfigService;

    @GetMapping("/all")
    public ResultData<PageList<InterfaceWorkflowNodeView>> getAllNodes(
            @RequestParam(value = "flowId", required = false) Integer flowId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        try {
            PageList<InterfaceWorkflowNodeView> nodes = interfaceFlowNodeService.getAllNodes(flowId, pageNum, pageSize);

            return ResponseRenderUtils.render(nodes);
        } catch (Exception e) {
            log.error("获取工作流节点失败", e);
            return ResponseRenderUtils.error("获取工作流节点失败: " + e.getMessage());
        }
    }

    @GetMapping("/all-unpaged")
    public ResultData<List<InterfaceWorkflowNodeView>> getAllNodesWithoutPaged() {
        try {
            List<InterfaceWorkflowNodeView> nodes = interfaceFlowNodeService.getAllNodesWithoutPaged();

            return ResponseRenderUtils.render(nodes);
        } catch (Exception e) {
            log.error("获取工作流节点失败", e);
            return ResponseRenderUtils.error("获取工作流节点失败: " + e.getMessage());
        }
    }



    /**
     * 创建工作流节点
     */
    @PostMapping("/create")
    public ResultData<Integer> createNode(@RequestBody WorkflowNodeCreateReq nodeRequest) {
        try {
            int savedNode = interfaceFlowNodeService.save(nodeRequest);
            return ResponseRenderUtils.render(savedNode);
        } catch (Exception e) {
            log.error("创建工作流节点失败", e);
            return ResponseRenderUtils.error("创建工作流节点失败: " + e.getMessage());
        }
    }

    /**
     * 根据节点ID获取工作流节点
     */
    @GetMapping("/{nodeId}")
    public ResultData<WorkflowNode> getNodeById(@PathVariable Integer nodeId) {
        try {
            WorkflowNode node = interfaceFlowNodeService.selectByNodeId(nodeId);
            if (node == null) {
                return ResponseRenderUtils.error("工作流节点不存在");
            }
            return ResponseRenderUtils.render(node);
        } catch (Exception e) {
            log.error("获取工作流节点失败", e);
            return ResponseRenderUtils.error("获取工作流节点失败: " + e.getMessage());
        }
    }

    /**
     * 更新工作流节点
     */
    @PostMapping("/update")
    public ResultData<Integer> updateNode(@RequestBody WorkflowNodeUpdateReq updateRequest) {
        try {
            int result = interfaceFlowNodeService.update(updateRequest);
            return ResponseRenderUtils.render(result);
        } catch (Exception e) {
            log.error("更新工作流节点失败", e);
            return ResponseRenderUtils.error("更新工作流节点失败: " + e.getMessage());
        }
    }

    @PostMapping("/update-schedule")
    public ResultData<Integer> updateNodeSchedule(@RequestBody WorkflowNodeScheduleUpdateReq updateRequest) {
        try {
            int result = interfaceFlowNodeService.updateSchedule(updateRequest);
            return ResponseRenderUtils.render(result);
        } catch (Exception e) {
            log.error("更新节点调度配置失败", e);
            return ResponseRenderUtils.error("更新节点调度配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除工作流节点
     */
    @DeleteMapping("/{nodeId}")
    public ResultData<String> deleteNode(@PathVariable Integer nodeId) {
        try {
            interfaceFlowNodeService.deleteByNodeId(nodeId);
            return ResponseRenderUtils.render("工作流节点删除成功");
        } catch (Exception e) {
            log.error("删除工作流节点失败", e);
            return ResponseRenderUtils.error("删除工作流节点失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{nodeId}/delete-all-config")
    public ResultData<String> deleteAllConfig(@PathVariable Integer nodeId) {
        try {
            interfaceFlowNodeService.deleteAllConfigByNodeId(nodeId);
            return ResponseRenderUtils.render("工作流节点配置删除成功");
        } catch (Exception e) {
            log.error("删除工作流节点配置失败", e);
            return ResponseRenderUtils.error("删除工作流节点配置失败: " + e.getMessage());
        }
    }

    @GetMapping("/export/{nodeId}")
    public ResultData<NodeParamConfigExportRsp> exportNodeParamConfig(@PathVariable Integer nodeId) {
        try {
            NodeParamConfigExportRsp nodeParamConfigExportResponse = interfaceFlowNodeService.exportNodeParamConfig(nodeId);

            return ResponseRenderUtils.render(nodeParamConfigExportResponse);
        } catch (Exception e) {
            log.error("获取工作流节点失败", e);
            return ResponseRenderUtils.error("获取工作流节点失败: " + e.getMessage());
        }
    }

    /**
     * 导入节点参数配置
     */
    @PostMapping("/import")
    public ResultData<Void> importNodeParamConfig(
            @RequestParam("nodeId") Integer nodeId,
            @RequestParam("file") MultipartFile file) {
        try {
            if (nodeId == null) {
                return ResponseRenderUtils.error("nodeId不能为空");
            }

            if (file == null || file.isEmpty()) {
                return ResponseRenderUtils.error("文件不能为空");
            }

            nodeParamConfigService.importNodeParamConfig(nodeId, file);
            return ResponseRenderUtils.success();
        } catch (Exception e) {
            log.error("导入节点参数配置失败", e);
            return ResponseRenderUtils.error("导入节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 导入节点配置
     */
    @PostMapping("/import-node")
    public ResultData<Void> importNodeConfig(
            @RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseRenderUtils.error("文件不能为空");
            }

            interfaceFlowNodeService.importNodeConfig(file);
            return ResponseRenderUtils.success();
        } catch (Exception e) {
            log.error("导入节点参数配置失败", e);
            return ResponseRenderUtils.error("导入节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 导出节点配置
     */
    @GetMapping("/export-node/{nodeId}")
    public ResultData<NodeConfigExportRsp> exportNodeConfig(@PathVariable Integer nodeId) {
        try {
            NodeConfigExportRsp nodeConfigExportRsp = interfaceFlowNodeService.exportNodeConfig(nodeId);

            return ResponseRenderUtils.render(nodeConfigExportRsp);
        } catch (Exception e) {
            log.error("获取工作流节点失败", e);
            return ResponseRenderUtils.error("获取工作流节点失败: " + e.getMessage());
        }
    }

    /**
     * 获取节点的下游调度节点
     */
    @GetMapping("/{nodeId}/downstream")
    public ResultData<List<InterfaceWorkflowNodeView>> getDownstreamNodes(@PathVariable Integer nodeId) {
        try {
            List<InterfaceWorkflowNodeView> downstreamNodes = interfaceFlowNodeService.getDownstreamNodes(nodeId);
            return ResponseRenderUtils.render(downstreamNodes);
        } catch (Exception e) {
            log.error("获取下游节点失败", e);
            return ResponseRenderUtils.error("获取下游节点失败: " + e.getMessage());
        }
    }
}
