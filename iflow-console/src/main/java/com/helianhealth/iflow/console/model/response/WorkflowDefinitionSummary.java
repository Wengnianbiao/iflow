package com.helianhealth.iflow.console.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowDefinitionSummary {

    private String name;

    private int version;

    private String displayName;

    private String description;

    private Long createTime;

    private Long updateTime;
}
