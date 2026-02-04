package com.helianhealth.agent.log.model;

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
    
    /**
     * 节点ID
     */
    private String nodeId;
    
    /**
     * 节点名称
     */
    private String nodeName;
    
    /**
     * 调用次数
     */
    private Integer count;
    
    /**
     * 平均耗时（毫秒）
     */
    private Double averageTime;
    
    /**
     * 最快耗时（毫秒）
     */
    private Integer minTime;
    
    /**
     * 最慢耗时（毫秒）
     */
    private Integer maxTime;
}
