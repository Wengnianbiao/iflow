package com.helianhealth.agent.ai.model;

import lombok.Data;

@Data
public class WorkflowNodeCreateMcpRequest {

    private String nodeName;
    private Integer flowId;
    private String paramFilterExpr;
    private String nodeType;
    private String metaInfo;
    private String splitField;
    private String scheduleExpr;
    private String scheduleParamSourceType;
    private String keywordField;
    private String remoteExtension;
    private String extensionParam;
}
