package com.helianhealth.agent.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 视图 - Mock 配置
 *
 * <p>对外屏蔽 {@link com.helianhealth.agent.repository.model.domain.MockConfig} 数据库实体，
 * 字段与其它 MCP View 对齐，避免直接暴露领域模型。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockConfigMcpView {

    /**
     * Mock 配置 ID（查询/更新/删除时使用，创建时不填）
     */
    private Integer mockId;

    /**
     * 所属节点 ID（创建时必填）
     */
    private Integer nodeId;

    /**
     * Mock 用例名称
     */
    private String caseName;

    /**
     * Mock 用例描述
     */
    private String caseDesc;

    /**
     * 模拟三方响应内容（按 workflow.contentType 写 JSON 或 XML 字符串）
     */
    private String mockResponse;
}
