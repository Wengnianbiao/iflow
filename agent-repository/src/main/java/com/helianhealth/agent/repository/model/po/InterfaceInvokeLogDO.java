package com.helianhealth.agent.repository.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

/**
 * 接口调用日志记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "interface_invoke_log")
public class InterfaceInvokeLogDO {

    @Id
    @Column(name = "log_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "keyword")
    private String keyword;

    @Column(name = "business_data")
    private String businessData;

    @Column(name = "param_before_invoke")
    private String paramBeforeInvoke;

    @Column(name = "remote_invoke_response")
    private String remoteInvokeResponse;

    @Column(name = "remote_invoke_response_parsed")
    private String remoteInvokeResponseParsed;

    @Column(name = "param_after_invoke")
    private String paramAfterInvoke;

    @Column(name = "invoke_time")
    private String invokeTime;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "create_time")
    private Date createTime;
}
