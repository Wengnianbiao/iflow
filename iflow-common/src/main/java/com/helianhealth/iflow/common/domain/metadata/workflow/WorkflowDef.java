package com.helianhealth.iflow.common.domain.metadata.workflow;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.helianhealth.iflow.common.domain.metadata.Auditable;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工作流定义。
 *
 * <p>整份定义序列化为 JSON 后存入 {@code meta_workflow_def.json_data}，
 * 以 {@code (route, version)} 唯一标识并版本化；{@code name} 为控制台展示名(通常中文名)，
 * {@code description} 描述业务用途。
 */
public class WorkflowDef extends Auditable {

    /**
     * 工作流标识，作为 Gateway 路由键，上游调用工作流只能通过 route 路由调用
     * （Jarvis {@code BusinessMethod} / Open API 去掉前导 {@code /} 的路径）。
     */
    private String route;

    /**
     * 版本号，从 1 起递增
     */
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
    @JsonDeserialize(using = TimeoutSecondsDeserializer.class)
    private long timeoutSeconds = 0;

    /**
     * 超时终止或仅记录告警；未配置时默认终止。
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private TimeoutPolicy timeoutPolicy = TimeoutPolicy.TIME_OUT_WF;

    /**
     * 关键字字段表达式，启动时解析后写入运行时实例的 {@code correlationId}。
     */
    private String correlationExpression;

    /**
     * 工作流级入参声明
     */
    private List<String> inputParameters = new LinkedList<>();

    /**
     * 工作流级出参映射
     */
    private Map<String, Object> outputParameters = new HashMap<>();

    /**
     * 全部任务定义
     */
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
     * 工作流超时策略。
     */
    public enum TimeoutPolicy {
        TIME_OUT_WF,
        ALERT_ONLY
    }

    /** 只接受 JSON 整数；负数由定义保存校验拒绝，历史快照仍可读取。 */
    public static class TimeoutSecondsDeserializer extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Long.class, "timeoutSeconds 必须是非负整数秒");
            }
            return parser.getLongValue();
        }
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

    public String getRoute() {
        return this.route;
    }

    public int getVersion() {
        return this.version;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public List<String> getTags() {
        return this.tags;
    }

    public long getTimeoutSeconds() {
        return this.timeoutSeconds;
    }

    public TimeoutPolicy getTimeoutPolicy() {
        return this.timeoutPolicy;
    }

    public String getCorrelationExpression() {
        return this.correlationExpression;
    }

    public List<String> getInputParameters() {
        return this.inputParameters;
    }

    public Map<String, Object> getOutputParameters() {
        return this.outputParameters;
    }

    public List<WorkflowTask> getTasks() {
        return this.tasks;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @JsonDeserialize(using = TimeoutSecondsDeserializer.class)
    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @JsonSetter(nulls = Nulls.SKIP)
    public void setTimeoutPolicy(TimeoutPolicy timeoutPolicy) {
        this.timeoutPolicy = timeoutPolicy;
    }

    public void setCorrelationExpression(String correlationExpression) {
        this.correlationExpression = correlationExpression;
    }

    public void setInputParameters(List<String> inputParameters) {
        this.inputParameters = inputParameters;
    }

    public void setOutputParameters(Map<String, Object> outputParameters) {
        this.outputParameters = outputParameters;
    }

    public void setTasks(List<WorkflowTask> tasks) {
        this.tasks = tasks;
    }

    public WorkflowDef() {
    }

    public WorkflowDef(
            String route,
            int version,
            String name,
            String description,
            List<String> tags,
            long timeoutSeconds,
            TimeoutPolicy timeoutPolicy,
            String correlationExpression,
            List<String> inputParameters,
            Map<String, Object> outputParameters,
            List<WorkflowTask> tasks) {
        this.route = route;
        this.version = version;
        this.name = name;
        this.description = description;
        this.tags = tags;
        this.timeoutSeconds = timeoutSeconds;
        this.timeoutPolicy = timeoutPolicy;
        this.correlationExpression = correlationExpression;
        this.inputParameters = inputParameters;
        this.outputParameters = outputParameters;
        this.tasks = tasks;
    }

    protected boolean canEqual(Object other) {
        return other instanceof WorkflowDef;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof WorkflowDef other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getRoute(), other.getRoute())
                && getVersion() == other.getVersion()
                && Objects.equals(getName(), other.getName())
                && Objects.equals(getDescription(), other.getDescription())
                && Objects.equals(getTags(), other.getTags())
                && getTimeoutSeconds() == other.getTimeoutSeconds()
                && Objects.equals(getTimeoutPolicy(), other.getTimeoutPolicy())
                && Objects.equals(getCorrelationExpression(), other.getCorrelationExpression())
                && Objects.equals(getInputParameters(), other.getInputParameters())
                && Objects.equals(getOutputParameters(), other.getOutputParameters())
                && Objects.equals(getTasks(), other.getTasks());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getRoute(),
                getVersion(),
                getName(),
                getDescription(),
                getTags(),
                getTimeoutSeconds(),
                getTimeoutPolicy(),
                getCorrelationExpression(),
                getInputParameters(),
                getOutputParameters(),
                getTasks());
    }

    @Override
    public String toString() {
        return "WorkflowDef(route=" + getRoute()
                + ", version=" + getVersion()
                + ", name=" + getName()
                + ", description=" + getDescription()
                + ", tags=" + getTags()
                + ", timeoutSeconds=" + getTimeoutSeconds()
                + ", timeoutPolicy=" + getTimeoutPolicy()
                + ", correlationExpression=" + getCorrelationExpression()
                + ", inputParameters=" + getInputParameters()
                + ", outputParameters=" + getOutputParameters()
                + ", tasks=" + getTasks() + ")";
    }

    public static class WorkflowDefBuilder {

        private String route;
        private int version = 1;
        private String name;
        private String description;
        private List<String> tags;
        private boolean tagsSet;
        private long timeoutSeconds = 0;
        private TimeoutPolicy timeoutPolicy = TimeoutPolicy.TIME_OUT_WF;
        private String correlationExpression;
        private List<String> inputParameters;
        private boolean inputParametersSet;
        private Map<String, Object> outputParameters;
        private boolean outputParametersSet;
        private List<WorkflowTask> tasks;
        private boolean tasksSet;

        WorkflowDefBuilder() {
        }

        public WorkflowDefBuilder route(String route) {
            this.route = route;
            return this;
        }

        public WorkflowDefBuilder version(int version) {
            this.version = version;
            return this;
        }

        public WorkflowDefBuilder name(String name) {
            this.name = name;
            return this;
        }

        public WorkflowDefBuilder description(String description) {
            this.description = description;
            return this;
        }

        public WorkflowDefBuilder tags(List<String> tags) {
            this.tags = tags;
            this.tagsSet = true;
            return this;
        }

        public WorkflowDefBuilder timeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public WorkflowDefBuilder timeoutPolicy(TimeoutPolicy timeoutPolicy) {
            this.timeoutPolicy = timeoutPolicy;
            return this;
        }

        public WorkflowDefBuilder correlationExpression(String correlationExpression) {
            this.correlationExpression = correlationExpression;
            return this;
        }

        public WorkflowDefBuilder inputParameters(List<String> inputParameters) {
            this.inputParameters = inputParameters;
            this.inputParametersSet = true;
            return this;
        }

        public WorkflowDefBuilder outputParameters(Map<String, Object> outputParameters) {
            this.outputParameters = outputParameters;
            this.outputParametersSet = true;
            return this;
        }

        public WorkflowDefBuilder tasks(List<WorkflowTask> tasks) {
            this.tasks = tasks;
            this.tasksSet = true;
            return this;
        }

        public WorkflowDef build() {
            return new WorkflowDef(
                    route,
                    version,
                    name,
                    description,
                    tagsSet ? tags : new ArrayList<>(),
                    timeoutSeconds,
                    timeoutPolicy,
                    correlationExpression,
                    inputParametersSet ? inputParameters : new LinkedList<>(),
                    outputParametersSet ? outputParameters : new HashMap<>(),
                    tasksSet ? tasks : new ArrayList<>());
        }

        @Override
        public String toString() {
            return "WorkflowDef.WorkflowDefBuilder(route=" + route
                    + ", version=" + version
                    + ", name=" + name
                    + ", description=" + description
                    + ", tags=" + tags
                    + ", timeoutSeconds=" + timeoutSeconds
                    + ", timeoutPolicy=" + timeoutPolicy
                    + ", correlationExpression=" + correlationExpression
                    + ", inputParameters=" + inputParameters
                    + ", outputParameters=" + outputParameters
                    + ", tasks=" + tasks + ")";
        }
    }

    public static WorkflowDefBuilder builder() {
        return new WorkflowDefBuilder();
    }
}
