package com.helianhealth.agent.console.controller.reponse;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SQL执行响应
 */
@Data
@Builder
public class SqlExecuteResponse {
    
    /**
     * 列名列表
     */
    private List<String> columns;
    
    /**
     * 数据行列表（每行是一个Map，key为列名，value为值）
     */
    private List<Map<String, Object>> rows;
}
