package com.helianhealth.agent.tasks.mapping;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 参数映射系统任务：合并自定义输入参数并按 {@code paramsConfig} 映射出参，映射字段直接写入 {@code outputData} 根级。
 */
@Component(TaskType.TASK_TYPE_MAPPING)
@Slf4j
public class ParamMappingTask extends WorkflowSystemTask {

    private final ParamMappingEngine paramMappingEngine;

    public ParamMappingTask(ParamMappingEngine paramMappingEngine) {
        super(TaskType.TASK_TYPE_MAPPING);
        this.paramMappingEngine = paramMappingEngine;
    }

    @Override
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> input = task.getInputData();
        try {
            Map<String, Object> output = paramMappingEngine.map(input);
            task.setOutputData(output);
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error(
                    "MAPPING task failed: workflow={}, task={}",
                    workflow.getWorkflowId(),
                    task.getTaskId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForInCompletion(msg);
            addError(task, msg);
        }
    }
}
