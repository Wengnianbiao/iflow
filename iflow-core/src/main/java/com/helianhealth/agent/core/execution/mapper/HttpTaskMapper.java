package com.helianhealth.agent.core.execution.mapper;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 任务 Mapper：调度阶段解析 {@code inputParameters}，Task 只负责发 HTTP。
 */
@Component
public class HttpTaskMapper implements TaskMapper {

    private final ParametersUtils parametersUtils;

    public HttpTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_HTTP;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        WorkflowTask workflowTask = context.getWorkflowTask();
        Map<String, Object> inputParameters = new HashMap<>(workflowTask.getInputParameters());
        Map<String, Object> input = parametersUtils.getTaskInput(
                inputParameters, context.getWorkflowModel(), context.getTaskId());

        TaskModel httpTask = context.createTaskModel();
        httpTask.setInputData(input);
        httpTask.setStatus(TaskModel.Status.SCHEDULED);
        httpTask.setRetryCount(context.getRetryCount());
        return List.of(httpTask);
    }
}
