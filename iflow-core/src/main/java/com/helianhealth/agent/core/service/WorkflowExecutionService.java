package com.helianhealth.agent.core.service;

import com.helianhealth.agent.common.domain.run.StartWorkflowRequest;
import com.helianhealth.agent.core.model.WorkflowModel;

/**
 * 工作流执行应用服务：将 {@link StartWorkflowRequest} 交给引擎同步执行。
 *
 * <p>Gateway / Console 只需传入请求；定义由引擎按 name/version 解析。
 */
public interface WorkflowExecutionService {

    /**
     * 启动工作流并同步执行至终态。
     */
    WorkflowModel startWorkflow(StartWorkflowRequest request);
}
