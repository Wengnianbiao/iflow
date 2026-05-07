package com.helianhealth.agent.console.controller.request;

import com.fasterxml.jackson.annotation.JsonAlias;

public class WorkflowExtensionBindingUpdateReq {

    private Integer flowId;

    /**
     * 为空表示清除绑定，回落 default。
     */
    @JsonAlias("spiExtensionName")
    private String routeExtension;

    public Integer getFlowId() {
        return flowId;
    }

    public void setFlowId(Integer flowId) {
        this.flowId = flowId;
    }

    public String getRouteExtension() {
        return routeExtension;
    }

    public void setRouteExtension(String routeExtension) {
        this.routeExtension = routeExtension;
    }
}
