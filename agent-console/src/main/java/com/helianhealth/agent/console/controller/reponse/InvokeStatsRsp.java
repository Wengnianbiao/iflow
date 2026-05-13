package com.helianhealth.agent.console.controller.reponse;

import lombok.Data;

/**
 * 调用统计视图
 */
@Data
public class InvokeStatsRsp {

    private String nodeId;

    private String nodeName;

    private Integer count;

    private Double averageTime;

    private Integer minTime;

    private Integer maxTime;
}
