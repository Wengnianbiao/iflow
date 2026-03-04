package com.helianhealth.agent.invocation.remote.core;

import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.param.model.ParamTreeNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Slf4j
public class DefaultApiClientProxy implements InterfaceClientProxy {

    @Override
    public Map<String, Object> doInvoke(WorkflowNode flowNode, List<ParamTreeNode> params) {
        // 没有三方调用的API直接返回参数
        return new HashMap<>();
    }

}
