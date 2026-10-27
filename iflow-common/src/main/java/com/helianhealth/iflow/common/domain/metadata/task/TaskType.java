package com.helianhealth.iflow.common.domain.metadata.task;

import java.util.HashSet;
import java.util.Set;

/**
 * 任务类型
 */
public enum TaskType {

    DECISION,
    SWITCH,
    FORK_JOIN,
    FORK,
    FORK_JOIN_DYNAMIC,
    JOIN,
    EXCLUSIVE_JOIN,
    DO_WHILE,
    INLINE,
    TERMINATE,
    NOOP,
    HTTP,
    JDBC,
    WEBSERVICE,
    MAPPING,
    SPI,
    USER_DEFINED;

    public static final String TASK_TYPE_DECISION = "DECISION";
    public static final String TASK_TYPE_SWITCH = "SWITCH";
    public static final String TASK_TYPE_FORK_JOIN = "FORK_JOIN";
    public static final String TASK_TYPE_FORK = "FORK";
    public static final String TASK_TYPE_FORK_JOIN_DYNAMIC = "FORK_JOIN_DYNAMIC";
    public static final String TASK_TYPE_JOIN = "JOIN";
    public static final String TASK_TYPE_EXCLUSIVE_JOIN = "EXCLUSIVE_JOIN";
    public static final String TASK_TYPE_DO_WHILE = "DO_WHILE";
    public static final String TASK_TYPE_INLINE = "INLINE";
    public static final String TASK_TYPE_TERMINATE = "TERMINATE";
    public static final String TASK_TYPE_NOOP = "NOOP";
    public static final String TASK_TYPE_HTTP = "HTTP";
    public static final String TASK_TYPE_JDBC = "JDBC";
    public static final String TASK_TYPE_WEBSERVICE = "WEBSERVICE";
    public static final String TASK_TYPE_MAPPING = "MAPPING";
    public static final String TASK_TYPE_SPI = "SPI";

    private static final Set<String> BUILT_IN_TASKS = new HashSet<>();

    static {
        BUILT_IN_TASKS.add(TASK_TYPE_DECISION);
        BUILT_IN_TASKS.add(TASK_TYPE_SWITCH);
        BUILT_IN_TASKS.add(TASK_TYPE_FORK);
        BUILT_IN_TASKS.add(TASK_TYPE_JOIN);
        BUILT_IN_TASKS.add(TASK_TYPE_EXCLUSIVE_JOIN);
        BUILT_IN_TASKS.add(TASK_TYPE_DO_WHILE);
    }

    /**
     * 将任务类型字符串转为 {@link TaskType}。未知类型默认返回 {@link TaskType#USER_DEFINED}。
     *
     * <p>如不需要默认值，请使用 {@link Enum#valueOf(Class, String)}。
     *
     * @param taskType 任务类型字符串
     * @return 对应的 {@link TaskType} 枚举
     */
    public static TaskType of(String taskType) {
        try {
            return TaskType.valueOf(taskType);
        } catch (IllegalArgumentException iae) {
            return TaskType.USER_DEFINED;
        }
    }

    public static boolean isBuiltIn(String taskType) {
        return BUILT_IN_TASKS.contains(taskType);
    }
}
