package com.helianhealth.agent.repository.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 接口调用日志领域模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeLog {

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
