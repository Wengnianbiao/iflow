package com.helianhealth.agent.repository.model.assembler;

import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowPO;

/**
 * 工作流 PO/Domain 双向转换器
 */
public final class WorkflowAssembler {

    private WorkflowAssembler() {
    }

    public static Workflow toDomain(InterfaceWorkflowPO entity) {
        if (entity == null) {
            return null;
        }
        return Workflow.builder()
                .flowId(entity.getFlowId())
                .flowName(entity.getFlowName())
                .interfaceUri(entity.getInterfaceUri())
                .contentType(entity.getContentType())
                .contentMetaInfo(entity.getContentMetaInfo())
                .firstFlowNodes(entity.getFirstFlowNodes())
                .status(entity.getStatus())
                .build();
    }

    public static InterfaceWorkflowPO toPO(Workflow workflow) {
        if (workflow == null) {
            return null;
        }
        return InterfaceWorkflowPO.builder()
                .flowId(workflow.getFlowId())
                .flowName(workflow.getFlowName())
                .interfaceUri(workflow.getInterfaceUri())
                .contentType(workflow.getContentType())
                .contentMetaInfo(workflow.getContentMetaInfo())
                .firstFlowNodes(workflow.getFirstFlowNodes())
                .status(workflow.getStatus())
                .build();
    }
}
