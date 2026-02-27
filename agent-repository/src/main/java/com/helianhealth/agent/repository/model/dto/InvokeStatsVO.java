package com.helianhealth.agent.repository.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接口调用统计结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeStatsVO {

    private String nodeId;

    private String nodeName;

    private Integer count;

    private Double averageTime;

    private Integer minTime;

    private Integer maxTime;
}
