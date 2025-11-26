package com.helianhealth.agent.service;

import com.helianhealth.agent.common.PageList;
import com.helianhealth.agent.controller.workflow.reponse.InterfaceWorkflowNodeView;
import com.helianhealth.agent.controller.workflow.reponse.NodeConfigExportRsp;
import com.helianhealth.agent.controller.workflow.reponse.NodeParamConfigExportRsp;
import com.helianhealth.agent.controller.workflow.request.WorkflowNodeCreateReq;
import com.helianhealth.agent.controller.workflow.request.WorkflowNodeUpdateReq;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface InterfaceWorkflowNodeService {

    InterfaceWorkflowNodeView selectByNodeId(Integer nodeId);

    PageList<InterfaceWorkflowNodeView> getAllNodes(Integer flowId, int pageNum, int pageSize);

    List<InterfaceWorkflowNodeView> getAllNodesWithoutPaged();

    /**
     * 执行工作流节点,对于工作流节点不care上游是jarvis还是啥，只管调用所以用Map是最合适的
     * @param flowNode 工作流节点
     * @param businessData 业务数据,统一入参为Map高度抽象
     * @return 统一出参为Map高度抽象
     */
    Map<String, Object> executeFlowNode(InterfaceWorkflowNode flowNode, Map<String, Object> businessData);

    int save(WorkflowNodeCreateReq nodeCreateReq);

    int update(WorkflowNodeUpdateReq nodeUpdateReq);

    void deleteByNodeId(Integer nodeId);

    void deleteAllConfigByNodeId(Integer nodeId);

    NodeParamConfigExportRsp exportNodeParamConfig(Integer nodeId);

    NodeConfigExportRsp exportNodeConfig(Integer nodeId);

    void importNodeConfig(MultipartFile file);
}
