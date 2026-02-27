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

    private Integer flowId;

    private String flowName;

    private String interfaceUri;

    private WorkflowContentType contentType;

    private String contentMetaInfo;

    private List<Integer> firstFlowNodes;

    private Integer status;
}
