package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.workflow.model.dto.ParamTreeNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Slf4j
public class DefaultApiClientProxy implements InterfaceClientProxy {

    @Override
    public Map<String, Object> doInvoke(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {
        // 没有三方调用的API直接返回参数
        return new HashMap<>();
    }

}
