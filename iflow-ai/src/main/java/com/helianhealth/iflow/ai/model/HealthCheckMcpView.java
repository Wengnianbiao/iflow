package com.helianhealth.iflow.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 健康检查视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckMcpView {

    /**
     * 整体状态：UP / DOWN
     */
    private String status;

    /**
     * MCP 服务名称
     */
    private String serverName;

    /**
     * MCP 服务版本
     */
    private String serverVersion;

    /**
     * 数据库连通性：UP / DOWN / SKIPPED
     */
    private String database;

    /**
     * 补充说明（失败原因等）
     */
    private String message;
}
