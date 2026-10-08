package com.helianhealth.iflow.console.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowDefinitionSummary {

    private String route;

    private int version;

    private String name;

    private String description;

    private Long createTime;

    private Long updateTime;
}
