package com.helianhealth.agent.core.model;

import com.helianhealth.agent.common.domain.run.Task;
import com.helianhealth.agent.common.domain.run.Workflow;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 引擎内 {@link WorkflowModel}/{@link TaskModel} 与对外运行时 {@link Workflow}/{@link Task} 互转。
 */
public final class WorkflowExecutionMapper {

    private WorkflowExecutionMapper() {}

    /** 持久化 workflow 头（不含 tasks 列表）。 */
    public static Workflow toWorkflowHeader(WorkflowModel model) {
        if (model == null) {
            return null;
        }
        Workflow workflow = new Workflow();
        workflow.setStatus(mapWorkflowStatus(model.getStatus()));
        workflow.setPreviousStatus(mapWorkflowStatus(model.getPreviousStatus()));
        workflow.setWorkflowId(model.getWorkflowId());
        workflow.setCorrelationId(model.getCorrelationId());
        workflow.setInput(copyMap(model.getInput()));
        workflow.setOutput(copyOutput(model.getOutput()));
        workflow.setWorkflowDefinition(model.getWorkflowDefinition());
        workflow.setCreateTime(model.getStartTime());
        workflow.setEndTime(model.getEndTime());
        workflow.setUpdateTime(model.getUpdateTime());
        workflow.setLastRetriedTime(model.getLastRetriedTime());
        workflow.setReasonForIncompletion(model.getReasonForInCompletion());
        workflow.setFailedTaskId(model.getFailedTaskId());
        workflow.setFailedReferenceTaskNames(copySet(model.getFailedReferenceTaskNames()));
        workflow.setFailedTaskNames(copySet(model.getFailedTaskNames()));
        workflow.setTasks(new LinkedList<>());
        return workflow;
    }

    public static WorkflowModel toModel(Workflow workflow) {
        if (workflow == null) {
            return null;
        }
        WorkflowModel model = new WorkflowModel();
        model.setStatus(mapModelStatus(workflow.getStatus()));
        model.setPreviousStatus(mapModelStatus(workflow.getPreviousStatus()));
        model.setWorkflowId(workflow.getWorkflowId());
        model.setCorrelationId(workflow.getCorrelationId());
        model.setInput(copyMap(workflow.getInput()));
        model.setOutput(copyOutput(workflow.getOutput()));
        model.setWorkflowDefinition(workflow.getWorkflowDefinition());
        model.setStartTime(workflow.getCreateTime());
        model.setEndTime(workflow.getEndTime());
        model.setUpdateTime(workflow.getUpdateTime());
        model.setLastRetriedTime(workflow.getLastRetriedTime());
        model.setReasonForInCompletion(workflow.getReasonForIncompletion());
        model.setFailedTaskId(workflow.getFailedTaskId());
        model.setFailedReferenceTaskNames(copySet(workflow.getFailedReferenceTaskNames()));
        model.setFailedTaskNames(copySet(workflow.getFailedTaskNames()));
        if (workflow.getTasks() != null) {
            model.setTasks(
                    workflow.getTasks().stream()
                            .map(WorkflowExecutionMapper::toTaskModel)
                            .collect(Collectors.toCollection(LinkedList::new)));
        }
        return model;
    }

    public static Task toTask(TaskModel model) {
        if (model == null) {
            return null;
        }
        Task task = new Task();
        task.setTaskId(model.getTaskId());
        task.setWorkflowInstanceId(model.getWorkflowInstanceId());
        task.setWorkflowType(model.getWorkflowType());
        task.setTaskType(model.getTaskType());
        task.setTaskDefName(model.getTaskDefName());
        task.setReferenceTaskName(model.getReferenceTaskName());
        task.setStatus(mapTaskStatus(model.getStatus()));
        task.setPreviousStatus(mapTaskStatus(model.getPreviousStatus()));
        task.setInputData(copyMap(model.getInputData()));
        task.setOutputData(copyOutput(model.getOutputData()));
        task.setRetryCount(model.getRetryCount());
        task.setSeq(model.getSeq());
        task.setRetriedTaskId(model.getRetriedTaskId());
        task.setRetried(model.isRetried());
        task.setExecuted(model.isExecuted());
        task.setCorrelationId(model.getCorrelationId());
        task.setScheduledTime(model.getScheduledTime());
        task.setFirstScheduledTime(model.getFirstScheduledTime());
        task.setStartTime(model.getStartTime());
        task.setEndTime(model.getEndTime());
        task.setUpdateTime(model.getUpdateTime());
        task.setReasonForIncompletion(model.getReasonForInCompletion());
        task.setWorkflowTask(model.getWorkflowTask());
        task.setParentTaskId(model.getParentTaskId());
        task.setForkTaskId(model.getForkTaskId());
        task.setIteration(model.getIteration());
        return task;
    }

    public static TaskModel toTaskModel(Task task) {
        if (task == null) {
            return null;
        }
        TaskModel model = new TaskModel();
        model.setTaskId(task.getTaskId());
        model.setWorkflowInstanceId(task.getWorkflowInstanceId());
        model.setWorkflowType(task.getWorkflowType());
        model.setTaskType(task.getTaskType());
        model.setTaskDefName(task.getTaskDefName());
        model.setReferenceTaskName(task.getReferenceTaskName());
        model.setStatus(mapModelTaskStatus(task.getStatus()));
        model.setPreviousStatus(mapModelTaskStatus(task.getPreviousStatus()));
        model.setInputData(copyMap(task.getInputData()));
        model.setOutputData(copyOutput(task.getOutputData()));
        model.setRetryCount(task.getRetryCount());
        model.setSeq(task.getSeq());
        model.setRetriedTaskId(task.getRetriedTaskId());
        model.setRetried(task.isRetried());
        model.setExecuted(task.isExecuted());
        model.setCorrelationId(task.getCorrelationId());
        model.setScheduledTime(task.getScheduledTime());
        model.setFirstScheduledTime(task.getFirstScheduledTime());
        model.setStartTime(task.getStartTime());
        model.setEndTime(task.getEndTime());
        model.setUpdateTime(task.getUpdateTime());
        model.setReasonForInCompletion(task.getReasonForIncompletion());
        model.setWorkflowTask(task.getWorkflowTask());
        model.setParentTaskId(task.getParentTaskId());
        model.setForkTaskId(task.getForkTaskId());
        model.setIteration(task.getIteration());
        return model;
    }

    private static Workflow.WorkflowStatus mapWorkflowStatus(WorkflowModel.Status status) {
        return status == null ? null : Workflow.WorkflowStatus.valueOf(status.name());
    }

    private static WorkflowModel.Status mapModelStatus(Workflow.WorkflowStatus status) {
        return status == null ? null : WorkflowModel.Status.valueOf(status.name());
    }

    private static Task.Status mapTaskStatus(TaskModel.Status status) {
        return status == null ? null : Task.Status.valueOf(status.name());
    }

    private static TaskModel.Status mapModelTaskStatus(Task.Status status) {
        return status == null ? null : TaskModel.Status.valueOf(status.name());
    }

    private static HashMap<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new HashMap<>() : new HashMap<>(source);
    }

    /** 保持输出根节点的类型和 null 语义，并复制可变容器。 */
    public static Object copyOutput(Object source) {
        if (source instanceof Map<?, ?> map) {
            Map<String, Object> copy = new HashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), copyOutput(value)));
            return copy;
        }
        if (source instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(value -> copy.add(copyOutput(value)));
            return copy;
        }
        return source;
    }

    private static HashSet<String> copySet(java.util.Set<String> source) {
        return source == null ? new HashSet<>() : new HashSet<>(source);
    }
}
