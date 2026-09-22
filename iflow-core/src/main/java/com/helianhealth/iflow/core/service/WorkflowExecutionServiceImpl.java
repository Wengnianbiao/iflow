package com.helianhealth.iflow.core.service;

import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.execution.StartWorkflowInput;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.springframework.stereotype.Service;

/**
 * {@link WorkflowExecutionService} 默认实现：将请求转为引擎入参后委托 {@link WorkflowExecutor}。
 *
 * <p>工作流定义由引擎按 name/version 解析，本层不查询元数据。
 */
@Service
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final WorkflowExecutor workflowExecutor;

    public WorkflowExecutionServiceImpl(WorkflowExecutor workflowExecutor) {
        this.workflowExecutor = workflowExecutor;
    }

    @Override
    public WorkflowModel startWorkflow(StartWorkflowRequest request) {
        return workflowExecutor.startWorkflow(StartWorkflowInput.from(request));
    }
}
