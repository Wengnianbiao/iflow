package com.helianhealth.agent.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP响应 - 节点参数配置查询/导出结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeParamConfigMcpResponse {

    /**
     * 节点ID
     */
    private Integer nodeId;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 前置处理参数配置（树形结构）
     */
    private List<NodeParamConfigMcpView> preProcessParams;

    /**
     * 后置处理参数配置（树形结构）
     */
    private List<NodeParamConfigMcpView> postProcessParams;

    /**
     * 配置总数
     */
    private Integer totalCount;
}
