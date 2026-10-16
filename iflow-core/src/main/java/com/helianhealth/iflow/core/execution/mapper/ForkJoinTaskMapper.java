package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * FORK_JOIN 任务 Mapper
 */
@Component
public class ForkJoinTaskMapper implements TaskMapper {

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_FORK_JOIN;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        WorkflowTask forkJoinTask = taskMapperContext.getWorkflowTask();
        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        int retryCount = taskMapperContext.getRetryCount();

        List<TaskModel> tasksToBeScheduled = new LinkedList<>();
        TaskModel forkTask = taskMapperContext.createTaskModel();
        forkTask.setTaskType(TaskType.TASK_TYPE_FORK);
        forkTask.setTaskDefName(TaskType.TASK_TYPE_FORK);
        forkTask.setInputData(taskInput);
        forkTask.setStatus(TaskModel.Status.IN_PROGRESS);
        tasksToBeScheduled.add(forkTask);

        List<List<WorkflowTask>> forkTasks = forkJoinTask.getForkTasks();
        for (List<WorkflowTask> branchTasks : forkTasks) {
            WorkflowTask branchTask = branchTasks.get(0);
            List<TaskModel> branchTasksToBeScheduled = taskMapperContext
                    .getDeciderService()
                    .getTasksToBeScheduled(
                            workflowModel,
                            branchTask,
                            retryCount,
                            null,
                            forkTask.getTaskId());
            branchTasksToBeScheduled.forEach(
                    branchRuntimeTask -> branchRuntimeTask.setParentTaskId(forkTask.getTaskId()));
            tasksToBeScheduled.addAll(branchTasksToBeScheduled);
        }

        WorkflowTask joinWorkflowTask =
                workflowModel
                        .getWorkflowDefinition()
                        .getNextTask(forkJoinTask.getTaskReferenceName());
        if (joinWorkflowTask == null
                || !TaskType.TASK_TYPE_JOIN.equals(joinWorkflowTask.getType())) {
            throw new TerminateWorkflowException(
                    "Fork task definition is not followed by a join task. Check the blueprint");
        }

        List<TaskModel> joinTask = taskMapperContext.getDeciderService()
                .getTasksToBeScheduled(
                        workflowModel,
                        joinWorkflowTask,
                        retryCount,
                        null,
                        taskMapperContext.getForkTaskId());

        tasksToBeScheduled.addAll(joinTask);
        return tasksToBeScheduled;
    }
}
