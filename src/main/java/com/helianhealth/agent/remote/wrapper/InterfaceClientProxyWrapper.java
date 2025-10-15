package com.helianhealth.agent.remote.wrapper;

import com.helianhealth.agent.model.domain.InterfaceWorkflowNodeDO;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.InterfaceClientProxy;
import jdk.nashorn.internal.objects.annotations.Setter;

import javax.swing.*;
import java.util.List;
import java.util.Map;


public class InterfaceClientProxyWrapper implements InterfaceClientProxy {


    private final InterfaceClientProxy interfaceClientProxy;



    public InterfaceClientProxyWrapper(InterfaceClientProxy interfaceClientProxy){
        this.interfaceClientProxy = interfaceClientProxy;
    }

    @Override
    public Map<String, Object> doInvoke(InterfaceWorkflowNodeDO flowNode, List<ParamTreeNode> params) {
        System.out.println("wrapper before");
        Map<String, Object> stringObjectMap = interfaceClientProxy.doInvoke(flowNode, params);
        System.out.println("wrapper after");
        return stringObjectMap;
    }
}
