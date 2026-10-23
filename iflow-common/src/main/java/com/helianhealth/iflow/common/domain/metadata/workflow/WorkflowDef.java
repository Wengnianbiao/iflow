package com.helianhealth.iflow.common.domain.metadata.workflow;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
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
 * 以 {@code (route, version)} 唯一标识并版本化；{@code name} 为控制台展示名(通常中文名)，
 * {@code description} 描述业务用途。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class WorkflowDef extends Auditable {

    /**
     * 工作流标识，作为 Gateway 路由键，上游调用工作流只能通过 route 路由调用
     * （Jarvis {@code BusinessMethod} / Open API 去掉前导 {@code /} 的路径）。
     */
    private String route;

    /**
     * 版本号，从 1 起递增
     */
    @Builder.Default
    private int version = 1;

    /**
     * 工作流名称；允许中文和重复名称，不参与调度定位
     */
    private String name;

    /**
     * 工作流描述
     */
    private String description;

    /**
     * 控制台分类标签，随定义 JSON 迁移，属于当前 route/version。
     */
    @Builder.Default
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<String> tags = new ArrayList<>();

    public static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .toList());
    }

    /**
     * 执行期限（秒）。{@code <= 0} 时继承系统默认值
     * {@code iflow.workflow-execution.default-timeout-seconds}。
     */
    @Builder.Default
    private long timeoutSeconds = 0;

    /**
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
     * {@code route.version} 形式的复合键
     */
    public String key() {
        return getKey(route, version);
    }

    public static String getKey(String route, int version) {
        return route + "." + version;
    }

    /**
     * 标识符格式：字母或数字开头，可含 {@code . _ / -}（对齐 Gateway 入口名）。
     */
    public static boolean isValidRoute(String route) {
        return route != null && route.matches("^[A-Za-z0-9][A-Za-z0-9._/-]{0,254}$");
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

        // WorkflowDef.tasks 本质上是一条顺序链表相接,子任务嵌套在各个控制节点中
        // 其实是DFS算法，顺序迭代链表中搜索每个节点的嵌套控制任务
        Iterator<WorkflowTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            WorkflowTask task = iterator.next();
            if (taskReferenceName.equals(task.getTaskReferenceName())) {
                break;
            }

            WorkflowTask nextTask = task.next(taskReferenceName, null);
            if (nextTask != null) {
                return nextTask;
            } else if (TaskType.DO_WHILE.name().equals(task.getType())
                    && !task.getTaskReferenceName().equals(taskReferenceName)
                    && task.has(taskReferenceName)) {
                // 引用名在 DO_WHILE 里、又没有层内后继：返回 null，禁止跳出循环去调度 DO_WHILE 后面的任务
                // 因为还需要调度DO_WHILE自己去判断当前循环是否应当结束
                return null;
            }

            // 引用名在这棵子树里，但子树已经走到末尾：has(ref) 为真，停住，返回这一层的下一个兄弟。
            if (task.has(taskReferenceName)) {
                break;
            }
        }
        // 返回当前当前层的下一个兄弟
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }
}
