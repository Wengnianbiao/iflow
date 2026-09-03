package com.helianhealth.agent.common.domain.metadata.workflow;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 工作流任务定义
 *
 * <p>一次三方接口外呼（HTTP、JDBC）或控制流节点（SWITCH / FORK_JOIN 等）在定义层的表示。
 * {@link #taskReferenceName} 是工作流内唯一引用键
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTask {

    /**
     * 任务定义名称
     */
    private String name;

    /**
     * 工作流内唯一引用名（DAG 引用键）
     */
    private String taskReferenceName;

    /**
     * 任务描述
     */
    private String description;

    /**
     * 任务类型：HTTP | JDBC | SWITCH | FORK_JOIN | INLINE 等
     * 默认SPI走默认实现 do nothing
     */
    @Builder.Default
    private String type = TaskType.SPI.name();

    /**
     * 任务入参
     */
    @Builder.Default
    private Map<String, Object> inputParameters = new HashMap<>();

    /**
     * SWITCH 控制流：分支 case → 子任务列表
     */
    @Builder.Default
    private Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();

    /**
     * SWITCH 默认分支
     */
    private List<WorkflowTask> defaultCase;

    /**
     * FORK_JOIN 并行分支
     */
    @Builder.Default
    private List<List<WorkflowTask>> forkTasks = new LinkedList<>();

    /**
     * JOIN / EXCLUSIVE_JOIN 等待的分支引用名列表
     */
    @Builder.Default
    private List<String> joinOn = new LinkedList<>();

    /**
     * EXCLUSIVE_JOIN 默认分支引用名
     */
    @Builder.Default
    private List<String> defaultExclusiveJoinTask = new LinkedList<>();

    /**
     * SWITCH / 脚本类任务的求值器类型（如 graaljs、spel）
     */
    private String evaluatorType;

    /**
     * SWITCH / 脚本类任务的表达式
     */
    private String expression;

    /**
     * 可选任务：失败不阻塞工作流
     */
    private boolean optional;

    /**
     * 宽容任务：失败时若同批兄弟任务尚未全部终态则继续等待
     */
    private boolean permissive;

    /**
     * DO_WHILE 循环体任务列表
     */
    @JsonDeserialize(using = WorkflowTaskListDeserializer.class)
    @Builder.Default
    private List<WorkflowTask> loopOver = new LinkedList<>();

    /**
     * DO_WHILE 循环条件表达式
     */
    private String loopCondition;

    /**
     * DO_WHILE 列表迭代表达式；为空时使用 loopCondition 进行计数式循环。
     */
    private String items;

    /**
     * 任务级重试次数上限
     */
    private Integer retryCount;

    /**
     * 递归获取获取当前task的所有后代task & itself
     */
    public List<WorkflowTask> collectTasks() {
        List<WorkflowTask> tasks = new LinkedList<>();
        tasks.add(this);
        for (List<WorkflowTask> workflowTaskList : children()) {
            for (WorkflowTask workflowTask : workflowTaskList) {
                tasks.addAll(workflowTask.collectTasks());
            }
        }
        return tasks;
    }

    /**
     * 当前任务或其嵌套子任务是否包含指定引用名
     */
    public boolean has(String taskReferenceName) {
        if (taskReferenceName.equals(this.getTaskReferenceName())) {
            return true;
        }

        switch (TaskType.of(type)) {
            case SWITCH:
            case DO_WHILE:
            case FORK_JOIN:
                for (List<WorkflowTask> childList : children()) {
                    for (WorkflowTask child : childList) {
                        if (child.has(taskReferenceName)) {
                            return true;
                        }
                    }
                }
                break;
            default:
                break;
        }
        return false;
    }

    /**
     * 在嵌套子任务内部查找下一任务
     */
    public WorkflowTask next(String taskReferenceName, WorkflowTask parent) {
        TaskType taskType = TaskType.of(type);

        switch (taskType) {
            case DO_WHILE:
            case SWITCH:
                for (List<WorkflowTask> workflowTasks : children()) {
                    Iterator<WorkflowTask> iterator = workflowTasks.iterator();
                    while (iterator.hasNext()) {
                        WorkflowTask task = iterator.next();
                        if (task.getTaskReferenceName().equals(taskReferenceName)) {
                            break;
                        }
                        // 递归获取嵌套子任务是否有taskReferenceName
                        WorkflowTask nextTask = task.next(taskReferenceName, this);
                        if (nextTask != null) {
                            return nextTask;
                        }
                        if (task.has(taskReferenceName)) {
                            break;
                        }
                    }
                    if (iterator.hasNext()) {
                        return iterator.next();
                    }
                }
                if (taskType == TaskType.DO_WHILE && has(taskReferenceName)) {
                    return this;
                }
                break;
            case FORK_JOIN:
                boolean found = false;
                for (List<WorkflowTask> workflowTasks : children()) {
                    Iterator<WorkflowTask> iterator = workflowTasks.iterator();
                    while (iterator.hasNext()) {
                        WorkflowTask task = iterator.next();
                        if (taskReferenceName.equals(task.getTaskReferenceName())) {
                            found = true;
                            break;
                        }
                        WorkflowTask nextTask = task.next(taskReferenceName, this);
                        if (nextTask != null) {
                            return nextTask;
                        }
                        if (task.has(taskReferenceName)) {
                            break;
                        }
                    }
                    if (iterator.hasNext()) {
                        return iterator.next();
                    }
                    if (found && parent != null) {
                        return parent.next(this.taskReferenceName, parent);
                    }
                }
                break;
            default:
                break;
        }
        return null;
    }

    private Collection<List<WorkflowTask>> children() {
        Collection<List<WorkflowTask>> workflowTaskLists = new LinkedList<>();

        switch (TaskType.of(type)) {
            case SWITCH:
                workflowTaskLists.addAll(decisionCases.values());
                workflowTaskLists.add(defaultCase);
                break;
            case FORK_JOIN:
                workflowTaskLists.addAll(forkTasks);
                break;
            case DO_WHILE:
                workflowTaskLists.add(loopOver);
                break;
            default:
                break;
        }
        return workflowTaskLists;
    }
}
