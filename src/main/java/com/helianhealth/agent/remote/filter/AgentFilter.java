package com.helianhealth.agent.remote.filter;

import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.InterfaceClientProxy;
import org.apache.dubbo.common.extension.SPI;


import java.util.List;
import java.util.Map;

/**
 * SPI扩展agent过滤
 */
@SPI
public interface AgentFilter {

    Map<String, Object> doInvoke(InterfaceClientProxy next, InterfaceWorkflowNode flowNode, List<ParamTreeNode> params);
}
