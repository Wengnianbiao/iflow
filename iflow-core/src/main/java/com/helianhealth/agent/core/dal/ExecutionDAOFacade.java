package com.helianhealth.agent.core.dal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.core.dao.ExecutionDAO;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 执行态数据访问门面。
 *
 * <p>执行引擎通过本门面访问执行数据，避免直接依赖具体的 {@link ExecutionDAO}。后续引入索引、
 * 外部载荷存储或执行事件时，相关编排统一收口在这里，而不侵入工作流执行逻辑。
 */
@Component
public class ExecutionDAOFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionDAOFacade.class);

    private final ExecutionDAO executionDAO;

    private final ObjectMapper objectMapper;

    public ExecutionDAOFacade(ExecutionDAO executionDAO, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

    public List<String> getRunningWorkflowIds(String workflowName, int version) {
        return executionDAO.getRunningWorkflowIds(workflowName, version);
    }

    public List<String> getRunningWorkflowIds() {
        return executionDAO.getRunningWorkflowIds();
    }

    public List<WorkflowModel> getPendingWorkflowsByType(String workflowName, int version) {
        return executionDAO.getPendingWorkflowsByType(workflowName, version);
    }

    public long getPendingWorkflowCount(String workflowName) {
        return executionDAO.getPendingWorkflowCount(workflowName);
    }

    public List<WorkflowModel> getWorkflowsByType(
            String workflowName, long startTime, long endTime) {
        return executionDAO.getWorkflowsByType(workflowName, startTime, endTime);
    }

    public List<WorkflowModel> getWorkflowsByCorrelationId(
            String workflowName, String correlationId, boolean includeTasks) {
        return executionDAO.getWorkflowsByCorrelationId(
                workflowName, correlationId, includeTasks);
    }

    public List<WorkflowModel> listRecentInstances(
            String workflowName, int version, int limit) {
        return executionDAO.listRecentInstances(workflowName, version, limit);
    }

    public List<TaskModel> createTasks(List<TaskModel> tasks) {
        return executionDAO.createTasks(tasks);
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
