package com.helianhealth.agent.core.execution.mapper;

import com.helianhealth.agent.core.model.TaskModel;

import java.util.List;

/**
 * 将 {@link com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask} 映射为
 * {@link TaskModel}（Decider 调度阶段调用，占位符在此阶段由 {@link com.helianhealth.agent.core.utils.ParametersUtils} 解析）。
 */
public interface TaskMapper {

    String getTaskType();

    List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext);
}
