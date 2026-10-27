package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.lock.ExecutionLockService;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Objects;

/** 清理上次进程遗留实例，并兜底检查当前运行实例的超时；不恢复或推进调度。 */
@Component
public class WorkflowSweeper {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowSweeper.class);
    private final ExecutionDAOFacade executionDAOFacade;
    private final ExecutionLockService executionLockService;
    private final DeciderService deciderService;
    private final WorkflowTermination termination;
    private final long processStartTime;

    @Autowired
    public WorkflowSweeper(SystemTaskRegistry registry,
            @Autowired(required = false) ExecutionDAOFacade dao,
            @Autowired(required = false) ExecutionLockService locks,
            DeciderService deciderService) {
        this(registry, dao, locks, deciderService, ManagementFactory.getRuntimeMXBean().getStartTime());
    }

    WorkflowSweeper(SystemTaskRegistry registry, ExecutionDAOFacade dao, ExecutionLockService locks,
            DeciderService deciderService, long processStartTime) {
        this.executionDAOFacade = dao;
        this.executionLockService = locks;
        this.deciderService = deciderService;
        this.termination = new WorkflowTermination(registry, dao);
        this.processStartTime = processStartTime;
    }

    public void sweepRunningWorkflows(WorkflowExecutor executor) {
        if (executionDAOFacade == null) {
            return;
        }
        List<String> workflowIds = executionDAOFacade.getUnfinishedWorkflowIds();
        for (String workflowId : workflowIds) {
            try {
                sweepStored(workflowId, executor);
            } catch (RuntimeException error) {
                // 失败的实例保留在 pending 中，下一轮继续尝试。
                LOGGER.error("Failed to sweep workflow {}", workflowId, error);
            }
        }
    }

    public boolean sweepStored(String workflowId, WorkflowExecutor executor) {
        if (executionDAOFacade == null || workflowId == null || workflowId.isBlank()) {
            return false;
        }
        boolean locked = false;
        if (executionLockService != null) {
            locked = executionLockService.acquireLock(workflowId);
            if (!locked) {
                return false;
            }
        }
        try {
            WorkflowModel workflow = executionDAOFacade.getWorkflowModel(workflowId);
            if (workflow == null || workflow.getStatus() == null || workflow.getStatus().isTerminal()) {
                return false;
            }
            if (workflow.getStartTime() < processStartTime) {
                termination.terminate(workflow, new TerminateWorkflowException(
                        "进程重启，内存执行队列已丢失，无法继续执行", WorkflowModel.Status.INTERRUPTED),
                        Objects.requireNonNull(executor, "workflowExecutor"));
                return true;
            }
            return sweep(workflow, executor);
        } finally {
            if (locked) {
                executionLockService.releaseLock(workflowId);
            }
        }
    }

    /** 检查已加载的当前实例；调用方负责实例锁。返回是否进入终态。 */
    public boolean sweep(WorkflowModel workflow, WorkflowExecutor executor) {
        try {
            deciderService.checkWorkflowTimeout(workflow);
            return false;
        } catch (TerminateWorkflowException error) {
            termination.terminate(workflow, error, Objects.requireNonNull(executor, "workflowExecutor"));
            return true;
        }
    }
}
