package com.helianhealth.agent.remote;

import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.dto.ParamTreeNode;

import java.util.List;

public interface ParamResolver {

    Object resolveParamNodes(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params);
}
