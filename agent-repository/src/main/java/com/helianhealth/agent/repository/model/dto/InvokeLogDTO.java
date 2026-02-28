package com.helianhealth.agent.repository.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 调用日志写入请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeLogDTO {

    private String nodeName;

    private String keyword;

    private String businessData;

    private String paramBeforeInvoke;

    private String remoteInvokeResponse;

    private String paramAfterInvoke;

    private String invokeTime;

    private String errorMessage;

    private boolean success = true;
}
