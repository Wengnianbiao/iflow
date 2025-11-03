package com.helianhealth.agent.model.convert;

import com.helianhealth.agent.controller.workflow.reponse.InterfaceWorkflowNodeView;
import com.helianhealth.agent.model.po.InterfaceWorkflowNodePO;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowNodeConvert {

    public InterfaceWorkflowNodeView poConvertToView(InterfaceWorkflowNodePO po) {
        if (po == null) {
            return null;
        }

        InterfaceWorkflowNodeView view = new InterfaceWorkflowNodeView();
        view.setNodeId(po.getNodeId());
        view.setNodeName(po.getNodeName());
        view.setFlowId(po.getFlowId());
        view.setParamFilterExpr(po.getParamFilterExpr());
        view.setNodeType(po.getNodeType());
        view.setMetaInfo(po.getMetaInfo());
        view.setSplitRule(po.getSplitRule());
        view.setScheduleExpr(po.getScheduleExpr());
        view.setScheduleParamSourceType(po.getScheduleParamSourceType());
        view.setKeywordField(po.getKeywordField());
        view.setRemoteExtension(po.getRemoteExtension());

        return view;
    }
}
