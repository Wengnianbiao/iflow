package com.helianhealth.agent.workflow.model.dto;

import lombok.Data;

@Data
public class LogContext {
    private String nodeName;
    private String businessData = "{}";
    private String keyword;
    private String paramBeforeInvoke = "{}";
    private String remoteInvokeResponse = "{}";
    private String paramAfterInvoke = "{}";
    private String invokeTime;
}
