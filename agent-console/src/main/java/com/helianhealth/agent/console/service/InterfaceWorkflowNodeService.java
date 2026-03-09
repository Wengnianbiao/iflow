package com.helianhealth.agent.console.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.model.dto.InterfaceWorkflowNodeView;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.console.controller.reponse.NodeConfigExportRsp;
import com.helianhealth.agent.console.controller.reponse.NodeParamConfigExportRsp;
import com.helianhealth.agent.console.controller.request.WorkflowNodeCreateReq;
import com.helianhealth.agent.console.controller.request.WorkflowNodeScheduleUpdateReq;
import com.helianhealth.agent.console.controller.request.WorkflowNodeUpdateReq;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface InterfaceWorkflowNodeService {

    WorkflowNode selectByNodeId(Integer nodeId);

    PageList<InterfaceWorkflowNodeView> getAllNodes(Integer flowId, int pageNum, int pageSize);

    List<InterfaceWorkflowNodeView> getAllNodesWithoutPaged();

    int save(WorkflowNodeCreateReq nodeCreateReq);

    int update(WorkflowNodeUpdateReq nodeUpdateReq);

    int updateSchedule(WorkflowNodeScheduleUpdateReq scheduleUpdateReq);

    void deleteByNodeId(Integer nodeId);

    void deleteAllConfigByNodeId(Integer nodeId);

    NodeParamConfigExportRsp exportNodeParamConfig(Integer nodeId);

    NodeConfigExportRsp exportNodeConfig(Integer nodeId);

    void importNodeConfig(MultipartFile file);

    // 批量删除节点
    int batchDeleteByNodeIds(List<Integer> nodeIds);
    
    // 批量导入节点配置
    void batchImportNodeConfig(MultipartFile file);

    /**
     * 获取节点的下游调度节点信息
     * @param nodeId 节点ID
     * @return 下游节点列表
     */
    List<InterfaceWorkflowNodeView> getDownstreamNodes(Integer nodeId);
}
