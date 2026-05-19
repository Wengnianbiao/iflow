package com.helianhealth.agent.ai.model;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowCreateMcpRequest {

    private String flowName;
    private String interfaceUri;
    private String routeExtension;
    private String contentType;
    private String contentMetaInfo;
    private List<Integer> firstFlowNodes;
    private Integer status;
}
