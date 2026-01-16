package com.helianhealth.agent.extension;


import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class TestInvoker implements InterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(InterfaceWorkflowNode interfaceWorkflowNode, Invocation invocation) {
        log.info("测试SPI拓展！！");
        Map<String, Object> map = new HashMap<>();
        map.put("code", "200");
        map.put("message", "success");
        return map;
    }
}
