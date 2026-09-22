package com.helianhealth.iflow.common.domain.metadata.workflow;

import com.helianhealth.iflow.common.domain.metadata.Auditable;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 工作流定义。
 *
 * <p>整份定义序列化为 JSON 后存入 {@code meta_workflow_def.json_data}，
 * 以 {@code (name, version)} 唯一标识并版本化；{@code displayName} 为控制台展示名(通常中文名)，
 * {@code description} 描述业务用途。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class WorkflowDef extends Auditable {

    /**
     * 工作流标识，作为 Gateway 路由键，上游调用工作流只能通过name去路由调用
     * （Jarvis {@code BusinessMethod} / Open API 去掉前导 {@code /} 的路径）。
     */
    private String name;

    /**
     * 版本号，从 1 起递增
     */
    @Builder.Default
    private int version = 1;

    /**
     * 工作流展示名
     */
    private String displayName;

    /**
     * 工作流描述
     */
    private String description;

    /**
     * 执行期限（秒）。{@code <= 0} 时继承系统默认值
     * {@code iflow.workflow-execution.default-timeout-seconds}。
     */
    @Builder.Default
    private long timeoutSeconds = 0;

    /**
     * 与控制台草稿对齐的超时策略字段。到期后的 sweep 只终止当前运行实例，
     * 不按 Conductor 的 {@code ALERT_ONLY} 继续调度。
     */
    @Builder.Default
    private TimeoutPolicy timeoutPolicy = TimeoutPolicy.TIME_OUT_WF;

    /**
     * 关键字字段表达式，启动时解析后写入运行时实例的 {@code correlationId}。
     */
    private String correlationExpression;

    /**
     * 工作流级入参声明
     */
    @Builder.Default
    private List<String> inputParameters = new LinkedList<>();

    /**
     * 工作流级出参映射
     */
    @Builder.Default
    private Map<String, Object> outputParameters = new HashMap<>();

    /**
     * 全部任务定义
     */
    @Builder.Default
    private List<WorkflowTask> tasks = new ArrayList<>();

    /**
     * {@code name.version} 形式的复合键
     */
    public String key() {
        return getKey(name, version);
    }

    public static String getKey(String name, int version) {
        return name + "." + version;
    }

    /**
     * 标识符格式：字母或数字开头，可含 {@code . _ / -}（对齐 Gateway 入口名）。
     */
    public static boolean isValidName(String name) {
        return name != null && name.matches("^[A-Za-z0-9][A-Za-z0-9._/-]{0,254}$");
    }

    /**
     * 工作流超时策略。字段与控制台草稿兼容；运行时 sweep 到期后统一终止，不继续调度。
     */
    public enum TimeoutPolicy {
        TIME_OUT_WF,
        ALERT_ONLY
    }


    public WorkflowTask getTaskByRefName(String taskReferenceName) {
        return collectTasks().stream()
                .filter(
                        workflowTask ->
                                workflowTask.getTaskReferenceName().equals(taskReferenceName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 收集当前workflowDef的所有task(包括task嵌套task)
     */
    public List<WorkflowTask> collectTasks() {
        List<WorkflowTask> tasks = new LinkedList<>();
        for (WorkflowTask workflowTask : this.tasks) {
            tasks.addAll(workflowTask.collectTasks());
        }

        return tasks;
    }

    public WorkflowTask getNextTask(String taskReferenceName) {
        WorkflowTask workflowTask = getTaskByRefName(taskReferenceName);
        if (workflowTask != null && TaskType.TERMINATE.name().equals(workflowTask.getType())) {
            return null;
        }

        // 从workflowDef的顶层tasks迭代
        Iterator<WorkflowTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            WorkflowTask task = iterator.next();
            // 顶层task找到直接返回该task的next
            if (taskReferenceName.equals(task.getTaskReferenceName())) {
                break;
            }

            // 嵌套任务中获取next task
            WorkflowTask nextTask = task.next(taskReferenceName, null);
            if (nextTask != null) {
                return nextTask;
            } else if (TaskType.DO_WHILE.name().equals(task.getType())
                    && !task.getTaskReferenceName().equals(taskReferenceName)
                    && task.has(taskReferenceName)) {
                // If the task is child of Loop Task and at last position, return null.
                return null;
            }

            if (task.has(taskReferenceName)) {
                break;
            }
        }
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }
}
