package com.helianhealth.agent.console.controller.reponse;

import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import lombok.Data;

/**
 * 工作流节点视图
 */
@Data
public class WorkflowNodeViewRsp {

    private Integer nodeId;

    private String nodeName;

    private Integer flowId;

    private String paramFilterExpr;

    private NodeType nodeType;

    private String metaInfo;

    private String splitField;

    private String scheduleExpr;

    private ScheduleParamSourceType scheduleParamSourceType;

    private String keywordField;

    private String remoteExtension;

    private String extensionParam;
}
