package com.helianhealth.iflow.core.service;

import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.model.WorkflowModel;

/**
 * 工作流执行应用服务：将 {@link StartWorkflowRequest} 交给引擎启动或同步执行。
 *
 * <p>Gateway / Console 只需传入请求；定义由引擎按 route/version 解析。
 */
public interface WorkflowExecutionService {

    /**
     * 在调用线程完成首次决策后返回实例 ID，不等待异步任务完成。
     */
    String startWorkflow(StartWorkflowRequest request);

    /**
     * 启动工作流并等待执行至终态，适用于需要最终业务输出的协议入口。
     */
    WorkflowModel startWorkflowSync(StartWorkflowRequest request);
}
