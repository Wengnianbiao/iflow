package com.helianhealth.agent.ai.model;

import lombok.Data;

import java.util.Date;

/**
 * MCP 调用日志视图
 */
@Data
public class InvokeLogMcpView {

    private Long logId;

    private String nodeName;

    private String keyword;

    private String businessData;

    private String paramBeforeInvoke;

    private String remoteInvokeResponse;

    private String remoteInvokeResponseParsed;

    private String paramAfterInvoke;

    private String invokeTime;

    private String errorMessage;

    private Date createTime;
}
