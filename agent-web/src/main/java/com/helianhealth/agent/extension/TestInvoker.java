package com.helianhealth.agent.extension;


import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.remote.DefaultInterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class TestInvoker extends DefaultInterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(InterfaceWorkflowNode interfaceWorkflowNode, Invocation invocation) {
        log.info("测试SPI拓展！！");
        log.info("interfaceWorkflowNode: {}", JsonUtils.toJsonString(interfaceWorkflowNode));
        Map<String, Object> map = new HashMap<>();
        super.remoteInvoke(interfaceWorkflowNode, invocation);
        log.info("interfaceWorkflowNode222: {}", JsonUtils.toJsonString(interfaceWorkflowNode));
        map.put("code", "200");
        map.put("message", "success");
        return map;
    }
}
