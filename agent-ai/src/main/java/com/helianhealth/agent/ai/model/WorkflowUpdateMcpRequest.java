package com.helianhealth.agent.ai.model;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowUpdateMcpRequest {

    private Integer flowId;
    private String flowName;
    private String interfaceUri;
    private String routeExtension;
    private String contentType;
    private String contentMetaInfo;
    private List<Integer> firstFlowNodes;
    private Integer status;
}
