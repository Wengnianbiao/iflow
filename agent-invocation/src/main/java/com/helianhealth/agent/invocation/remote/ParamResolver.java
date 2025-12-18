package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.workflow.model.dto.ParamTreeNode;

import java.util.List;

public interface ParamResolver {

    Object resolveParamNodes(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params);
}
