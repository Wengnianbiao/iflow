package com.helianhealth.agent.extension;


import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Test2Invoker implements InterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(WorkflowNode workflowNode, Invocation invocation) {
        log.info("测试SPI拓展2222！！");
        Map<String, Object> map = new HashMap<>();
        map.put("code", "200");
        map.put("message", "success");
        return map;
    }
}
