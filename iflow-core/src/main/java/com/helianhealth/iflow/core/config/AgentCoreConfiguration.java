package com.helianhealth.iflow.core.config;

import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
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

@Configuration
@ComponentScan(basePackages = "com.helianhealth.iflow.core")
public class AgentCoreConfiguration {

    public static final String TASK_MAPPERS_BY_TASK_TYPE = "taskMappersByTaskType";
    public static final String ASYNC_TASK_EXECUTOR = "asyncTaskExecutor";

    @Bean
    @Qualifier(TASK_MAPPERS_BY_TASK_TYPE)
    public Map<String, TaskMapper> taskMappersByTaskType(List<TaskMapper> taskMappers) {
        return taskMappers.stream()
                .collect(toMap(TaskMapper::getTaskType, identity(), (a, b) -> a, HashMap::new));
    }

    /** 异步系统任务使用有界内存队列；MAPPING 和控制节点仍由当前决策线程执行。 */
    @Bean(name = ASYNC_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor asyncTaskExecutor(
            @Value(
                            "${iflow.workflow-execution.task-thread-count:${iflow.workflow-execution.fork-thread-count:32}}")
                    int threadCount,
            @Value(
                            "${iflow.workflow-execution.task-queue-capacity:${iflow.workflow-execution.fork-queue-capacity:256}}")
                    int queueCapacity) {
        if (threadCount <= 0) {
            throw new IllegalArgumentException("async task thread count must be positive");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException(
                    "async task queue capacity must not be negative");
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadCount);
        executor.setMaxPoolSize(threadCount);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("iflow-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }
}
