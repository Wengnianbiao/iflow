package com.helianhealth.agent.workflow.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.workflow.controller.reponse.InterfaceWorkflowNodeView;
import com.helianhealth.agent.workflow.controller.reponse.NodeConfigExportRsp;
import com.helianhealth.agent.workflow.controller.reponse.NodeParamConfigExportRsp;
import com.helianhealth.agent.workflow.controller.request.WorkflowNodeCreateReq;
import com.helianhealth.agent.workflow.controller.request.WorkflowNodeUpdateReq;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface InterfaceWorkflowNodeService {

    InterfaceWorkflowNodeView selectByNodeId(Integer nodeId);

    PageList<InterfaceWorkflowNodeView> getAllNodes(Integer flowId, int pageNum, int pageSize);

    List<InterfaceWorkflowNodeView> getAllNodesWithoutPaged();

    int save(WorkflowNodeCreateReq nodeCreateReq);

    int update(WorkflowNodeUpdateReq nodeUpdateReq);

    void deleteByNodeId(Integer nodeId);

    void deleteAllConfigByNodeId(Integer nodeId);

    NodeParamConfigExportRsp exportNodeParamConfig(Integer nodeId);

    NodeConfigExportRsp exportNodeConfig(Integer nodeId);

    void importNodeConfig(MultipartFile file);

    // 批量删除节点
    int batchDeleteByNodeIds(List<Integer> nodeIds);
    
    // 批量导入节点配置
    void batchImportNodeConfig(MultipartFile file);
}