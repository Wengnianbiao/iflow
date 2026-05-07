package com.helianhealth.agent.ai.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowCreateMcpRequest {

    private String flowName;
    private String interfaceUri;
    @JsonAlias("spiExtensionName")
    private String routeExtension;
    private String contentType;
    private String contentMetaInfo;
    private List<Integer> firstFlowNodes;
    private Integer status;
}
