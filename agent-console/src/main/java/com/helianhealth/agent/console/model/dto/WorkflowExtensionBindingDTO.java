package com.helianhealth.agent.console.model.dto;

import lombok.Data;

/**
 * 工作流与入口路由扩展绑定（列表展示）
 */
@Data
public class WorkflowExtensionBindingDTO {

    private Integer flowId;

    private String flowName;

    private String interfaceUri;

    private Integer status;

    private String routeExtension;
}
