package com.helianhealth.agent.schedule.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schedule 模块线程池配置。
 *
 * <p>并行节点编排与 split 拆批执行使用<strong>独立线程池</strong>，避免同池嵌套 {@code CompletableFuture.join()}
 * 导致父任务占满线程、子任务无法调度而长期 WAITING。</p>
 */
@Configuration
public class ScheduleThreadPoolConfig {

    @Bean("WorkFlowNodeExecutor")
    public ThreadPoolTaskExecutor workFlowNodeExecutor() {
        return createExecutor(
                "WorkFlowNodeExecutor",
                5,
                10,
                512
        );
    }

    @Bean("WorkFlowSplitExecutor")
    public ThreadPoolTaskExecutor workFlowSplitExecutor() {
        return createExecutor(
                "WorkFlowSplitExecutor",
                5,
                10,
                512
        );
    }

    private static ThreadPoolTaskExecutor createExecutor(String namePrefix,
                                                         int corePoolSize,
                                                         int maxPoolSize,
                                                         int queueCapacity) {
        AtomicInteger threadNumber = new AtomicInteger(1);
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setName(namePrefix + "-" + threadNumber.getAndIncrement());
            return thread;
        };

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadFactory(threadFactory);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
