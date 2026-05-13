package com.helianhealth.agent.repository.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接口调用统计领域模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeStats {

    private String nodeId;

    private String nodeName;

    private Integer count;

    private Double averageTime;

    private Integer minTime;

    private Integer maxTime;
}
