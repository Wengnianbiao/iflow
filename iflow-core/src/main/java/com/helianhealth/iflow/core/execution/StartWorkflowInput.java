package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class StartWorkflowInput {

    private String workflowId;

    private String route;

    private Integer version;

    private Map<String, Object> workflowInput = new HashMap<>();

    public StartWorkflowInput() {
    }

    public StartWorkflowInput(StartWorkflowRequest request) {
        if (request == null || !StringUtils.hasText(request.getRoute())) {
            throw new IllegalArgumentException("工作流 route 不能为空");
        }
        this.route = request.getRoute();
        this.version = request.getVersion();
        if (request.getInput() != null) {
            this.workflowInput = new HashMap<>(request.getInput());
        }
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Map<String, Object> getWorkflowInput() {
        return workflowInput;
    }

    public void setWorkflowInput(Map<String, Object> workflowInput) {
        this.workflowInput = workflowInput;
    }
}
