package com.helianhealth.iflow.core.service;

import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.execution.StartWorkflowInput;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.springframework.stereotype.Service;

/** 分别委托普通启动与等待终态两种引擎入口。 */
@Service
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final WorkflowExecutor workflowExecutor;

    public WorkflowExecutionServiceImpl(WorkflowExecutor workflowExecutor) {
        this.workflowExecutor = workflowExecutor;
    }

    @Override
    public String startWorkflow(StartWorkflowRequest request) {
        return workflowExecutor.startWorkflow(new StartWorkflowInput(request));
    }

    @Override
    public WorkflowModel startWorkflowSync(StartWorkflowRequest request) {
        return workflowExecutor.startWorkflowSync(new StartWorkflowInput(request));
    }
}
