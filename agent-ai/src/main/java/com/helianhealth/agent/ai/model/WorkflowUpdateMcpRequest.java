package com.helianhealth.agent.ai.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowUpdateMcpRequest {

    private Integer flowId;
    private String flowName;
    private String interfaceUri;
    @JsonAlias("spiExtensionName")
    private String routeExtension;
    private String contentType;
    private String contentMetaInfo;
    private List<Integer> firstFlowNodes;
    private Integer status;
}
