package com.helianhealth.agent.log.model;

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

    /**
     * 工作流节点名称
     */
    private String nodeName;

    /**
     * 业务关键字，用于日志检索，如 patientCode
     */
    private String keyword;

    /**
     * 业务入参数据（JSON 格式）
     */
    private String businessData;

    /**
     * 调用前参数，即前置映射后的请求参数（JSON 格式）
     */
    private String paramBeforeInvoke;

    /**
     * 远程调用原始响应（JSON 格式）
     */
    private String remoteInvokeResponse;

    /**
     * 调用后参数，即后置映射后的最终结果（JSON 格式）
     */
    private String paramAfterInvoke;

    /**
     * 调用耗时，如 "16 ms"
     */
    private String invokeTime;
}
