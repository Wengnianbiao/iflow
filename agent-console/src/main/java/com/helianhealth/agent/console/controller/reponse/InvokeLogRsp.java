package com.helianhealth.agent.console.controller.reponse;

import lombok.Data;

import java.util.Date;

/**
 * 调用日志视图
 */
@Data
public class InvokeLogRsp {

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
