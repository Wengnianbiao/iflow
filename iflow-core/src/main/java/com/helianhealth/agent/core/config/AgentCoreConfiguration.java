package com.helianhealth.agent.core.config;

import com.helianhealth.agent.core.execution.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * iflow-core Spring 配置
 */
@Configuration
@ComponentScan(basePackages = "com.helianhealth.agent.core")
public class AgentCoreConfiguration {

    public static final String TASK_MAPPERS_BY_TASK_TYPE = "taskMappersByTaskType";
    public static final String WORKFLOW_TASK_EXECUTOR = "workflowTaskExecutor";

    @Bean
    @Qualifier(TASK_MAPPERS_BY_TASK_TYPE)
    public Map<String, TaskMapper> taskMappersByTaskType(List<TaskMapper> taskMappers) {
        return taskMappers.stream()
                .collect(toMap(TaskMapper::getTaskType, identity(), (a, b) -> a, HashMap::new));
    }

    /**
     * 工作流任务共享执行池。Decider 只负责生成可运行任务，具体的
     * {@code WorkflowSystemTask.execute()} 不占用决策线程。
     */
    @Bean(name = WORKFLOW_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor workflowTaskExecutor(
            @Value("${iflow.workflow-execution.task-thread-count:32}") int threadCount,
            @Value("${iflow.workflow-execution.task-queue-capacity:256}") int queueCapacity) {
        if (threadCount <= 0) {
            throw new IllegalArgumentException("workflow task thread count must be positive");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("workflow task queue capacity must not be negative");
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadCount);
        executor.setMaxPoolSize(threadCount);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("iflow-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }
}
