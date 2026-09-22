package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.utils.ParametersUtils;

import java.util.List;

/**
 * 将 {@link WorkflowTask} 映射为
 * {@link TaskModel}（Decider 调度阶段调用，占位符在此阶段由 {@link ParametersUtils} 解析）。
 */
public interface TaskMapper {

    String getTaskType();

    List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext);
}
