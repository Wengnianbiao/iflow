package com.helianhealth.agent.repository.model.domain;

import com.helianhealth.agent.common.enums.WorkflowContentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工作流领域模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {

    /**
     * 工作流 ID
     */
    private Integer flowId;

    /**
     * 工作流名称
     */
    private String flowName;

    /**
     * 接口 URI
     */
    private String interfaceUri;

    /**
     * 内容类型
     */
    private WorkflowContentType contentType;

    /**
     * 内容元信息
     */
    private String contentMetaInfo;

    /**
     * 首个工作流节点 ID 列表
     */
    private List<Integer> firstFlowNodes;

    /**
     * 状态
     */
    private Integer status;
}
