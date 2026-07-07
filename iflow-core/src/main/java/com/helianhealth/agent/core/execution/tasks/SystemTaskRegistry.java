package com.helianhealth.agent.core.execution.tasks;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统任务注册中心
 *
 * <p>Spring 启动时收集容器内所有 {@link WorkflowSystemTask} Bean，
 * 按 {@link WorkflowSystemTask#getTaskType()} 建索引，引擎按任务 type 路由。
 */
@Component
public class SystemTaskRegistry {

    private final Map<String, WorkflowSystemTask> registry;
    public SystemTaskRegistry(Set<WorkflowSystemTask> tasks) {
        this.registry = tasks.stream()
                .collect(Collectors.toMap(WorkflowSystemTask::getTaskType, Function.identity()));
    }

    public WorkflowSystemTask get(String taskType) {
        return Optional.ofNullable(registry.get(taskType))
                .orElseThrow(() -> new IllegalStateException(
                        "system task not registered: " + taskType));
    }

    public boolean isSystemTask(String taskType) {
        return taskType != null && registry.containsKey(taskType);
    }
}
