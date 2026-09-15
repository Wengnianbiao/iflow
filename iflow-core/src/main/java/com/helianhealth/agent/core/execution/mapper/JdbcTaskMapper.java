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
 * JDBC 任务 Mapper：编排层解析平铺输入中的 ${...}，SQL 的 $.路径留给任务执行阶段。
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
        Map<String, Object> input = context.getTaskInput() != null
                ? new HashMap<>(context.getTaskInput())
                : parametersUtils.getTaskInput(
                        workflowTask.getInputParameters(), context.getWorkflowModel(), context.getTaskId());

        TaskModel jdbcTask = context.createTaskModel();
        jdbcTask.setInputData(input);
        jdbcTask.setStatus(TaskModel.Status.SCHEDULED);
        jdbcTask.setRetryCount(context.getRetryCount());
        return List.of(jdbcTask);
    }
}
