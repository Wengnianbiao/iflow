package com.helianhealth.agent.console.controller.request;

import lombok.Data;

@Data
public class WorkflowNodeScheduleUpdateReq {

    private Integer nodeId;
    private String scheduleParamSourceType;
    private String scheduleExpr;
}
