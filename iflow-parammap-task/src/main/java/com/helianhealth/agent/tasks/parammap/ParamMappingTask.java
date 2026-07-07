package com.helianhealth.agent.tasks.parammap;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 参数映射系统任务：按固定 {@code payload} + {@code paramsConfig} 映射出参，结果写入 {@code outputData.output}。
 */
@Component(TaskType.TASK_TYPE_PARAM_MAP)
@Slf4j
public class ParamMappingTask extends WorkflowSystemTask {

    public static final String OUTPUT = "output";

    private final ParamMappingEngine paramMappingEngine;

    public ParamMappingTask(ParamMappingEngine paramMappingEngine) {
        super(TaskType.TASK_TYPE_PARAM_MAP);
        this.paramMappingEngine = paramMappingEngine;
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> input = task.getInputData();
        try {
            Map<String, Object> output = paramMappingEngine.map(input);
            task.addOutput(OUTPUT, output);
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error(
                    "PARAM_MAP task failed: workflow={}, task={}",
                    workflow.getWorkflowId(),
                    task.getTaskId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForInCompletion(msg);
            task.addOutput("error", msg);
        }
        return true;
    }
}
