package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

/** 单次同步驱动的 FORK 结果交接、在途分支和等待通知，不参与任务登记。 */
public final class WorkflowDriveContext implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final Set<String> outstandingBranchIds = new HashSet<>();
    private final Queue<ExecutionEvent> events = new ConcurrentLinkedQueue<>();
    private final Map<String, TaskModel> dirtyTasks = new LinkedHashMap<>();
    private final Semaphore changed = new Semaphore(0);
    private final ExecutionDAOFacade executionDAOFacade;
    private volatile boolean closed;

    public WorkflowDriveContext() {
        this(null);
    }

    public WorkflowDriveContext(ExecutionDAOFacade executionDAOFacade) {
        this.executionDAOFacade = executionDAOFacade;
    }

    public boolean submitBranch(TaskModel task) {
        checkOwner();
        if (closed) {
            throw new IllegalStateException("工作流驱动已经结束");
        }
        if (outstandingBranchIds.contains(task.getTaskId())) {
            return false;
        }
        outstandingBranchIds.add(task.getTaskId());
        return true;
    }

    public int outstandingBranches() {
        checkOwner();
        return outstandingBranchIds.size();
    }

    public boolean isClosed() {
        return closed;
    }

    public void publishStarted(TaskModel target, long startTime) {
        publish(new Started(target, startTime));
    }

    public void publishCompleted(TaskModel target, TaskModel result) {
        publish(new Completed(target, result));
    }

    private void publish(ExecutionEvent event) {
        if (!closed) {
            events.add(event);
            changed.release();
        }
    }

    public boolean hasEvents() {
        return !events.isEmpty();
    }

    /** 完成事件总要移除在途分支，是否推进业务流程仍由 Decider 判断。 */
    public boolean consumeEvents(WorkflowModel workflow) {
        checkOwner();
        boolean consumed = false;
        ExecutionEvent event;
        while ((event = events.poll()) != null) {
            consumed = true;
            if (event instanceof Completed completed) {
                outstandingBranchIds.remove(completed.target().getTaskId());
                if (canPublish(workflow, completed.target())) {
                    mergeResult(completed.target(), completed.result());
                }
            } else if (event instanceof Started started && canPublish(workflow, started.target())) {
                markStarted(started.target(), started.startTime());
            }
        }
        return consumed;
    }

    private boolean canPublish(WorkflowModel workflow, TaskModel task) {
        return !closed && !workflow.getStatus().isTerminal()
                && (task.getStatus() == null || !task.getStatus().isTerminal());
    }

    public void markStarted(TaskModel task, long startTime) {
        checkOwner();
        task.setStartTime(startTime);
        task.setUpdateTime(startTime);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        markDirty(task);
        // 同步业务调用可能较慢，调用前就要让查询端看到 IN_PROGRESS。
        if (executionDAOFacade != null) {
            executionDAOFacade.updateTask(task);
        }
    }

    private void mergeResult(TaskModel target, TaskModel result) {
        // 先完整发布数据，最后设置终态。身份、执行域、循环关系由 caller 持有。
        target.setInputData(result.getInputData());
        target.setOutputData(result.getOutputData());
        target.setReasonForInCompletion(result.getReasonForInCompletion());
        target.setStartTime(result.getStartTime());
        target.setEndTime(result.getEndTime());
        target.setUpdateTime(result.getUpdateTime());
        target.setStatus(result.getStatus());
        markDirty(target);
    }

    public void markDirty(TaskModel task) {
        checkOwner();
        dirtyTasks.put(task.getTaskId(), task);
    }

    public List<TaskModel> drainDirtyTasks() {
        checkOwner();
        List<TaskModel> result = List.copyOf(dirtyTasks.values());
        dirtyTasks.clear();
        return result;
    }

    public void awaitChange() throws InterruptedException {
        checkOwner();
        changed.acquire();
        changed.drainPermits();
    }

    public void checkOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("只有工作流调用线程可以推进或登记任务");
        }
    }

    @Override
    public void close() {
        checkOwner();
        closed = true;
        outstandingBranchIds.clear();
        events.clear();
        dirtyTasks.clear();
    }

    private sealed interface ExecutionEvent permits Started, Completed {}
    private record Started(TaskModel target, long startTime) implements ExecutionEvent {}
    private record Completed(TaskModel target, TaskModel result) implements ExecutionEvent {}
}
