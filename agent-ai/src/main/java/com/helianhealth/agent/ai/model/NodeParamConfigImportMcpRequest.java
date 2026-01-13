package com.helianhealth.agent.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP请求 - 批量导入节点参数配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeParamConfigImportMcpRequest {

    /**
     * 节点ID（必填）
     */
    private Integer nodeId;

    /**
     * 是否清空旧配置后再导入（默认true）
     */
    private Boolean clearBeforeImport;

    /**
     * 参数配置列表
     */
    private List<NodeParamConfigMcpView> params;
}
