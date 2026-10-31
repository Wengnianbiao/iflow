package com.helianhealth.iflow.core.dal;

import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.exception.TransientException;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 执行态数据访问门面。
 */
@Component
public class ExecutionDAOFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionDAOFacade.class);

    private final ExecutionDAO executionDAO;

    public ExecutionDAOFacade(ExecutionDAO executionDAO) {
        this.executionDAO = executionDAO;
    }

    /**
     * 创建工作流实例。
     *
     * @return 已创建的工作流实例 ID
     */
    public String createWorkflow(WorkflowModel workflow) {
        executionDAO.createWorkflow(workflow);
        return workflow.getWorkflowId();
    }

    public String updateWorkflow(WorkflowModel workflow) {
        return executionDAO.updateWorkflow(workflow);
    }

    public Optional<WorkflowModel> getWorkflow(String workflowId, boolean includeTasks) {
        return executionDAO.getWorkflow(workflowId, includeTasks);
    }

    public void removeWorkflow(String workflowId) {
        executionDAO.removeWorkflow(workflowId);
    }

    public List<String> getRunningWorkflowIds(String workflowRoute, int version) {
        return executionDAO.getRunningWorkflowIds(workflowRoute, version);
    }

    public List<String> getRunningWorkflowIds() {
        return executionDAO.getRunningWorkflowIds();
    }

    public List<String> getUnfinishedWorkflowIds() {
        return executionDAO.getUnfinishedWorkflowIds();
    }

    public List<WorkflowModel> getPendingWorkflowsByType(String workflowRoute, int version) {
        return executionDAO.getPendingWorkflowsByType(workflowRoute, version);
    }

    public long getPendingWorkflowCount(String workflowRoute) {
        return executionDAO.getPendingWorkflowCount(workflowRoute);
    }

    public List<WorkflowModel> getWorkflowsByType(
            String workflowRoute, long startTime, long endTime) {
        return executionDAO.getWorkflowsByType(workflowRoute, startTime, endTime);
    }

    public List<WorkflowModel> getWorkflowsByCorrelationId(
            String workflowRoute, String correlationId, boolean includeTasks) {
        return executionDAO.getWorkflowsByCorrelationId(
                workflowRoute, correlationId, includeTasks);
    }

    public List<WorkflowModel> listRecentInstances(
            String workflowRoute, int version, int limit) {
        return executionDAO.listRecentInstances(workflowRoute, version, limit);
    }

    public List<TaskModel> createTasks(List<TaskModel> tasks) {
        return executionDAO.createTasks(tasks);
    }

    /** 去重、持久化并加入工作流运行时，不启动或调度任务。 */
    public List<TaskModel> registerTasks(WorkflowModel workflow, List<TaskModel> candidates) {
        Set<String> registeredKeys = new HashSet<>();
        int sequence = 0;
        for (TaskModel task : workflow.getTasks()) {
            registeredKeys.add(taskKey(task));
            sequence = Math.max(sequence, task.getSeq());
        }

        List<TaskModel> newTasks = new ArrayList<>();
        for (TaskModel task : candidates) {
            String key = taskKey(task);
            if (registeredKeys.contains(key)) {
                continue;
            }
            registeredKeys.add(key);
            newTasks.add(task);
        }
        if (newTasks.isEmpty()) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        for (TaskModel task : newTasks) {
            task.setSeq(++sequence);
            task.setStartTime(0);
            if (task.getStatus() == null || !task.getStatus().isTerminal()) {
                task.setStatus(TaskModel.Status.SCHEDULED);
            }
            if (task.getScheduledTime() == 0) {
                task.setScheduledTime(now);
            }
            if (task.getFirstScheduledTime() == 0) {
                task.setFirstScheduledTime(task.getScheduledTime());
            }
        }

        List<TaskModel> created = createTasks(newTasks);
        Set<String> createdIds = new HashSet<>();
        if (created != null) {
            for (TaskModel task : created) {
                createdIds.add(task.getTaskId());
            }
        }
        for (TaskModel task : newTasks) {
            if (!createdIds.contains(task.getTaskId())) {
                throw new TransientException("任务实例未成功登记，拒绝启动未持久化任务");
            }
        }

        workflow.getTasks().addAll(newTasks);
        updateWorkflow(workflow);
        return newTasks;
    }

    private String taskKey(TaskModel task) {
        return task.getReferenceTaskName() + "_" + task.getRetryCount();
    }

    public void updateTask(TaskModel task) {
        executionDAO.updateTask(task);
    }

    public Optional<TaskModel> getTask(String taskId) {
        return executionDAO.getTask(taskId);
    }

    public List<TaskModel> getTasks(List<String> taskIds) {
        return executionDAO.getTasks(taskIds);
    }

    public boolean removeTask(String taskId) {
        return executionDAO.removeTask(taskId);
    }

    public List<TaskModel> getTasksForWorkflow(String workflowId) {
        return executionDAO.getTasksForWorkflow(workflowId);
    }

    public List<TaskModel> getTasksByReferenceName(
            String workflowId, String referenceTaskName) {
        return executionDAO.getTasksByReferenceName(workflowId, referenceTaskName);
    }

    public List<TaskModel> getPendingTasksForTaskType(String taskType) {
        return executionDAO.getPendingTasksForTaskType(taskType);
    }

    public List<TaskModel> getPendingTasks() {
        return executionDAO.getPendingTasks();
    }

    public long getInProgressTaskCount(String taskDefName) {
        return executionDAO.getInProgressTaskCount(taskDefName);
    }

    public WorkflowModel getWorkflowModel(String workflowId) {
        return executionDAO.getWorkflow(workflowId, true).orElse(null);
    }

    public void updateTasks(List<TaskModel> tasks) {
        tasks.forEach(this::updateTask);
    }
}
