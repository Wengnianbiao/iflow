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
 * JDBC 任务 Mapper：调度阶段解析连接信息与 SQL 占位符。
 */
@Component
public class JdbcTaskMapper implements TaskMapper {

    private final ParametersUtils parametersUtils;

    public JdbcTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_JDBC;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        WorkflowTask workflowTask = context.getWorkflowTask();
        Map<String, Object> inputParameters = new HashMap<>(workflowTask.getInputParameters());
        Map<String, Object> input = parametersUtils.getTaskInput(
                inputParameters, context.getWorkflowModel(), context.getTaskId());

        TaskModel jdbcTask = context.createTaskModel();
        jdbcTask.setInputData(input);
        jdbcTask.setStatus(TaskModel.Status.SCHEDULED);
        jdbcTask.setRetryCount(context.getRetryCount());
        return List.of(jdbcTask);
    }
}
