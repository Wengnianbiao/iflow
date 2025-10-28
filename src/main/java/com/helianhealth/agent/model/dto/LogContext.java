package com.helianhealth.agent.model.dto;

import lombok.Data;

@Data
public class LogContext {
    private Integer nodeId;
    private String businessData = "{}";
    private String keyword;
    private String paramBeforeInvoke = "{}";
    private String remoteInvokeResponse = "{}";
    private String paramAfterInvoke = "{}";
    private String invokeTime;
}
