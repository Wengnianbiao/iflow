package com.helianhealth.agent.repository.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 调用日志查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeLogVO {

    private Long logId;

    private String nodeName;

    private String keyword;

    private String businessData;

    private String paramBeforeInvoke;

    private String remoteInvokeResponse;

    private String paramAfterInvoke;

    private String invokeTime;

    private Date createTime;
}
