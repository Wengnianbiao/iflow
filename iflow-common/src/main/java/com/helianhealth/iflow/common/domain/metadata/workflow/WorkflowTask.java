package com.helianhealth.iflow.common.domain.metadata.workflow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工作流任务定义
 *
 * <p>一次三方接口外呼（HTTP、JDBC）或控制流节点（SWITCH / FORK_JOIN 等）在定义层的表示。
 * {@link #taskReferenceName} 是工作流内唯一引用键
 */
@JsonIgnoreProperties({"timeoutSeconds", "timeoutPolicy"})
public class WorkflowTask {

    /**
     * 任务名称，用于展示，不关联独立的任务注册定义
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
    private String type = TaskType.SPI.name();

    /**
     * 任务入参
     */
    private Map<String, Object> inputParameters = new HashMap<>();

    /**
     * SWITCH 控制流：分支 case → 子任务列表
     */
    private Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();

    /**
     * SWITCH 默认分支
     */
    private List<WorkflowTask> defaultCase;

    /**
     * FORK_JOIN 并行分支
     */
    private List<List<WorkflowTask>> forkTasks = new LinkedList<>();

    /**
     * JOIN / EXCLUSIVE_JOIN 等待的分支引用名列表
     */
    private List<String> joinOn = new LinkedList<>();

    /**
     * EXCLUSIVE_JOIN 默认分支引用名
     */
    private List<String> defaultExclusiveJoinTask = new LinkedList<>();

    /**
     * SWITCH / 脚本类任务的求值器类型
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
    private List<WorkflowTask> loopOver = new LinkedList<>();

    /**
     * DO_WHILE 每轮结束后的 JS 继续条件，与 loopItems 互斥。
     */
    private String loopCondition;

    /**
     * DO_WHILE 数组来源表达式，与 loopCondition 互斥；进入循环时解析为固定快照。
     */
    private String loopItems;

    /**
     * 异步任务在首次执行之外允许的重试次数。四项策略为 null 时继承框架配置，显式 0 保留。
     */
    @JsonDeserialize(using = PolicyIntegerDeserializer.class)
    private Integer retryCount;

    /** 重试基础间隔（秒）。0 表示不等待。 */
    @JsonDeserialize(using = PolicyIntegerDeserializer.class)
    private Integer retryDelaySeconds;

    private RetryLogic retryLogic;

    /** 节点满足调度条件后的启动延迟（秒）。0 表示无延迟。 */
    @JsonDeserialize(using = PolicyIntegerDeserializer.class)
    private Integer startDelay;

    public enum RetryLogic {
        FIXED,
        LINEAR_BACKOFF,
        EXPONENTIAL_BACKOFF;

        @JsonCreator
        public static RetryLogic fromValue(String value) {
            return valueOf(value);
        }
    }

    /** 不将小数或字符串静默转换为整数；非负约束由定义保存入口检查。 */
    public static class PolicyIntegerDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Integer.class, "%s 必须是非负整数", parser.currentName());
            }
            return parser.getIntValue();
        }
    }

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
                // 循环体最后一格：返回 DO_WHILE 自己，让外层忽略重复创建
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
                    // 分支最后一格，且自己嵌在别的节点里：问父节点“我的下一个是谁”
                    if (found && parent != null) {
                        return parent.next(this.taskReferenceName, parent);
                    }
                }
                break;
            default:
                break;
        }
        // 到当前层级的末尾节点就没有后继节点了
        return null;
    }

    private Collection<List<WorkflowTask>> children() {
        Collection<List<WorkflowTask>> workflowTaskLists = new LinkedList<>();
        // 不同控制节点的子节点列表children
        switch (TaskType.of(type)) {
            case SWITCH:
                if (decisionCases != null) {
                    workflowTaskLists.addAll(decisionCases.values());
                }
                if (defaultCase != null) {
                    workflowTaskLists.add(defaultCase);
                }
                break;
            case FORK_JOIN:
                if (forkTasks != null) {
                    workflowTaskLists.addAll(forkTasks);
                }
                break;
            case DO_WHILE:
                if (loopOver != null) {
                    workflowTaskLists.add(loopOver);
                }
                break;
            default:
                break;
        }
        return workflowTaskLists;
    }

    public String getName() {
        return this.name;
    }

    public String getTaskReferenceName() {
        return this.taskReferenceName;
    }

    public String getDescription() {
        return this.description;
    }

    public String getType() {
        return this.type;
    }

    public Map<String, Object> getInputParameters() {
        return this.inputParameters;
    }

    public Map<String, List<WorkflowTask>> getDecisionCases() {
        return this.decisionCases;
    }

    public List<WorkflowTask> getDefaultCase() {
        return this.defaultCase;
    }

    public List<List<WorkflowTask>> getForkTasks() {
        return this.forkTasks;
    }

    public List<String> getJoinOn() {
        return this.joinOn;
    }

    public List<String> getDefaultExclusiveJoinTask() {
        return this.defaultExclusiveJoinTask;
    }

    public String getEvaluatorType() {
        return this.evaluatorType;
    }

    public String getExpression() {
        return this.expression;
    }

    public boolean isOptional() {
        return this.optional;
    }

    public boolean isPermissive() {
        return this.permissive;
    }

    public List<WorkflowTask> getLoopOver() {
        return this.loopOver;
    }

    public String getLoopCondition() {
        return this.loopCondition;
    }

    public String getLoopItems() {
        return this.loopItems;
    }

    public Integer getRetryCount() {
        return this.retryCount;
    }

    public Integer getRetryDelaySeconds() {
        return this.retryDelaySeconds;
    }

    public RetryLogic getRetryLogic() {
        return this.retryLogic;
    }

    public Integer getStartDelay() {
        return this.startDelay;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTaskReferenceName(String taskReferenceName) {
        this.taskReferenceName = taskReferenceName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setInputParameters(Map<String, Object> inputParameters) {
        this.inputParameters = inputParameters;
    }

    public void setDecisionCases(Map<String, List<WorkflowTask>> decisionCases) {
        this.decisionCases = decisionCases;
    }

    public void setDefaultCase(List<WorkflowTask> defaultCase) {
        this.defaultCase = defaultCase;
    }

    public void setForkTasks(List<List<WorkflowTask>> forkTasks) {
        this.forkTasks = forkTasks;
    }

    public void setJoinOn(List<String> joinOn) {
        this.joinOn = joinOn;
    }

    public void setDefaultExclusiveJoinTask(List<String> defaultExclusiveJoinTask) {
        this.defaultExclusiveJoinTask = defaultExclusiveJoinTask;
    }

    public void setEvaluatorType(String evaluatorType) {
        this.evaluatorType = evaluatorType;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public void setPermissive(boolean permissive) {
        this.permissive = permissive;
    }

    public void setLoopOver(List<WorkflowTask> loopOver) {
        this.loopOver = loopOver;
    }

    public void setLoopCondition(String loopCondition) {
        this.loopCondition = loopCondition;
    }

    public void setLoopItems(String loopItems) {
        this.loopItems = loopItems;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public void setRetryDelaySeconds(Integer retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public void setRetryLogic(RetryLogic retryLogic) {
        this.retryLogic = retryLogic;
    }

    public void setStartDelay(Integer startDelay) {
        this.startDelay = startDelay;
    }

    protected boolean canEqual(Object other) {
        return other instanceof WorkflowTask;
    }

    public WorkflowTask() {
    }

    public WorkflowTask(
            String name,
            String taskReferenceName,
            String description,
            String type,
            Map<String, Object> inputParameters,
            Map<String, List<WorkflowTask>> decisionCases,
            List<WorkflowTask> defaultCase,
            List<List<WorkflowTask>> forkTasks,
            List<String> joinOn,
            List<String> defaultExclusiveJoinTask,
            String evaluatorType,
            String expression,
            boolean optional,
            boolean permissive,
            List<WorkflowTask> loopOver,
            String loopCondition,
            String loopItems,
            Integer retryCount,
            Integer retryDelaySeconds,
            RetryLogic retryLogic,
            Integer startDelay) {
        this.name = name;
        this.taskReferenceName = taskReferenceName;
        this.description = description;
        this.type = type;
        this.inputParameters = inputParameters;
        this.decisionCases = decisionCases;
        this.defaultCase = defaultCase;
        this.forkTasks = forkTasks;
        this.joinOn = joinOn;
        this.defaultExclusiveJoinTask = defaultExclusiveJoinTask;
        this.evaluatorType = evaluatorType;
        this.expression = expression;
        this.optional = optional;
        this.permissive = permissive;
        this.loopOver = loopOver;
        this.loopCondition = loopCondition;
        this.loopItems = loopItems;
        this.retryCount = retryCount;
        this.retryDelaySeconds = retryDelaySeconds;
        this.retryLogic = retryLogic;
        this.startDelay = startDelay;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof WorkflowTask other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getName(), other.getName())
                && Objects.equals(getTaskReferenceName(), other.getTaskReferenceName())
                && Objects.equals(getDescription(), other.getDescription())
                && Objects.equals(getType(), other.getType())
                && Objects.equals(getInputParameters(), other.getInputParameters())
                && Objects.equals(getDecisionCases(), other.getDecisionCases())
                && Objects.equals(getDefaultCase(), other.getDefaultCase())
                && Objects.equals(getForkTasks(), other.getForkTasks())
                && Objects.equals(getJoinOn(), other.getJoinOn())
                && Objects.equals(getDefaultExclusiveJoinTask(), other.getDefaultExclusiveJoinTask())
                && Objects.equals(getEvaluatorType(), other.getEvaluatorType())
                && Objects.equals(getExpression(), other.getExpression())
                && isOptional() == other.isOptional()
                && isPermissive() == other.isPermissive()
                && Objects.equals(getLoopOver(), other.getLoopOver())
                && Objects.equals(getLoopCondition(), other.getLoopCondition())
                && Objects.equals(getLoopItems(), other.getLoopItems())
                && Objects.equals(getRetryCount(), other.getRetryCount())
                && Objects.equals(getRetryDelaySeconds(), other.getRetryDelaySeconds())
                && Objects.equals(getRetryLogic(), other.getRetryLogic())
                && Objects.equals(getStartDelay(), other.getStartDelay());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getName(),
                getTaskReferenceName(),
                getDescription(),
                getType(),
                getInputParameters(),
                getDecisionCases(),
                getDefaultCase(),
                getForkTasks(),
                getJoinOn(),
                getDefaultExclusiveJoinTask(),
                getEvaluatorType(),
                getExpression(),
                isOptional(),
                isPermissive(),
                getLoopOver(),
                getLoopCondition(),
                getLoopItems(),
                getRetryCount(),
                getRetryDelaySeconds(),
                getRetryLogic(),
                getStartDelay());
    }

    @Override
    public String toString() {
        return "WorkflowTask(name=" + getName()
                + ", taskReferenceName=" + getTaskReferenceName()
                + ", description=" + getDescription()
                + ", type=" + getType()
                + ", inputParameters=" + getInputParameters()
                + ", decisionCases=" + getDecisionCases()
                + ", defaultCase=" + getDefaultCase()
                + ", forkTasks=" + getForkTasks()
                + ", joinOn=" + getJoinOn()
                + ", defaultExclusiveJoinTask=" + getDefaultExclusiveJoinTask()
                + ", evaluatorType=" + getEvaluatorType()
                + ", expression=" + getExpression()
                + ", optional=" + isOptional()
                + ", permissive=" + isPermissive()
                + ", loopOver=" + getLoopOver()
                + ", loopCondition=" + getLoopCondition()
                + ", loopItems=" + getLoopItems()
                + ", retryCount=" + getRetryCount()
                + ", retryDelaySeconds=" + getRetryDelaySeconds()
                + ", retryLogic=" + getRetryLogic()
                + ", startDelay=" + getStartDelay() + ")";
    }

    public static class WorkflowTaskBuilder {

        private String name;
        private String taskReferenceName;
        private String description;
        private String type = TaskType.SPI.name();
        private Map<String, Object> inputParameters;
        private boolean inputParametersSet;
        private Map<String, List<WorkflowTask>> decisionCases;
        private boolean decisionCasesSet;
        private List<WorkflowTask> defaultCase;
        private List<List<WorkflowTask>> forkTasks;
        private boolean forkTasksSet;
        private List<String> joinOn;
        private boolean joinOnSet;
        private List<String> defaultExclusiveJoinTask;
        private boolean defaultExclusiveJoinTaskSet;
        private String evaluatorType;
        private String expression;
        private boolean optional;
        private boolean permissive;
        private List<WorkflowTask> loopOver;
        private boolean loopOverSet;
        private String loopCondition;
        private String loopItems;
        private Integer retryCount;
        private Integer retryDelaySeconds;
        private RetryLogic retryLogic;
        private Integer startDelay;

        WorkflowTaskBuilder() {
        }

        public WorkflowTaskBuilder name(String name) {
            this.name = name;
            return this;
        }

        public WorkflowTaskBuilder taskReferenceName(String taskReferenceName) {
            this.taskReferenceName = taskReferenceName;
            return this;
        }

        public WorkflowTaskBuilder description(String description) {
            this.description = description;
            return this;
        }

        public WorkflowTaskBuilder type(String type) {
            this.type = type;
            return this;
        }

        public WorkflowTaskBuilder inputParameters(Map<String, Object> inputParameters) {
            this.inputParameters = inputParameters;
            this.inputParametersSet = true;
            return this;
        }

        public WorkflowTaskBuilder decisionCases(Map<String, List<WorkflowTask>> decisionCases) {
            this.decisionCases = decisionCases;
            this.decisionCasesSet = true;
            return this;
        }

        public WorkflowTaskBuilder defaultCase(List<WorkflowTask> defaultCase) {
            this.defaultCase = defaultCase;
            return this;
        }

        public WorkflowTaskBuilder forkTasks(List<List<WorkflowTask>> forkTasks) {
            this.forkTasks = forkTasks;
            this.forkTasksSet = true;
            return this;
        }

        public WorkflowTaskBuilder joinOn(List<String> joinOn) {
            this.joinOn = joinOn;
            this.joinOnSet = true;
            return this;
        }

        public WorkflowTaskBuilder defaultExclusiveJoinTask(List<String> defaultExclusiveJoinTask) {
            this.defaultExclusiveJoinTask = defaultExclusiveJoinTask;
            this.defaultExclusiveJoinTaskSet = true;
            return this;
        }

        public WorkflowTaskBuilder evaluatorType(String evaluatorType) {
            this.evaluatorType = evaluatorType;
            return this;
        }

        public WorkflowTaskBuilder expression(String expression) {
            this.expression = expression;
            return this;
        }

        public WorkflowTaskBuilder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public WorkflowTaskBuilder permissive(boolean permissive) {
            this.permissive = permissive;
            return this;
        }

        public WorkflowTaskBuilder loopOver(List<WorkflowTask> loopOver) {
            this.loopOver = loopOver;
            this.loopOverSet = true;
            return this;
        }

        public WorkflowTaskBuilder loopCondition(String loopCondition) {
            this.loopCondition = loopCondition;
            return this;
        }

        public WorkflowTaskBuilder loopItems(String loopItems) {
            this.loopItems = loopItems;
            return this;
        }

        public WorkflowTaskBuilder retryCount(Integer retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public WorkflowTaskBuilder retryDelaySeconds(Integer retryDelaySeconds) {
            this.retryDelaySeconds = retryDelaySeconds;
            return this;
        }

        public WorkflowTaskBuilder retryLogic(RetryLogic retryLogic) {
            this.retryLogic = retryLogic;
            return this;
        }

        public WorkflowTaskBuilder startDelay(Integer startDelay) {
            this.startDelay = startDelay;
            return this;
        }

        public WorkflowTask build() {
            return new WorkflowTask(
                    name,
                    taskReferenceName,
                    description,
                    type,
                    inputParametersSet ? inputParameters : new HashMap<>(),
                    decisionCasesSet ? decisionCases : new LinkedHashMap<>(),
                    defaultCase,
                    forkTasksSet ? forkTasks : new LinkedList<>(),
                    joinOnSet ? joinOn : new LinkedList<>(),
                    defaultExclusiveJoinTaskSet ? defaultExclusiveJoinTask : new LinkedList<>(),
                    evaluatorType,
                    expression,
                    optional,
                    permissive,
                    loopOverSet ? loopOver : new LinkedList<>(),
                    loopCondition,
                    loopItems,
                    retryCount,
                    retryDelaySeconds,
                    retryLogic,
                    startDelay);
        }

        @Override
        public String toString() {
            return "WorkflowTask.WorkflowTaskBuilder(name=" + name
                    + ", taskReferenceName=" + taskReferenceName
                    + ", description=" + description
                    + ", type=" + type
                    + ", inputParameters=" + inputParameters
                    + ", decisionCases=" + decisionCases
                    + ", defaultCase=" + defaultCase
                    + ", forkTasks=" + forkTasks
                    + ", joinOn=" + joinOn
                    + ", defaultExclusiveJoinTask=" + defaultExclusiveJoinTask
                    + ", evaluatorType=" + evaluatorType
                    + ", expression=" + expression
                    + ", optional=" + optional
                    + ", permissive=" + permissive
                    + ", loopOver=" + loopOver
                    + ", loopCondition=" + loopCondition
                    + ", loopItems=" + loopItems
                    + ", retryCount=" + retryCount
                    + ", retryDelaySeconds=" + retryDelaySeconds
                    + ", retryLogic=" + retryLogic
                    + ", startDelay=" + startDelay + ")";
        }
    }

    public static WorkflowTaskBuilder builder() {
        return new WorkflowTaskBuilder();
    }
}
