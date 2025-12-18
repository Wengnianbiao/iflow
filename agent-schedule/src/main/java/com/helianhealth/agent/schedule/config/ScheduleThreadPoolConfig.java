package com.helianhealth.agent.schedule.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Schedule模块 - 线程池配置
 */
@Configuration
public class ScheduleThreadPoolConfig {

    @Bean("WorkFlowNodeExecutor")
    public ThreadPoolTaskExecutor WorkFlowNodeExecutor() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .setNameFormat("WorkFlowNodeExecutor-thread-pool-%d")
            .build();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //配置核心线程数
        executor.setCorePoolSize(5);
        //配置最大线程数
        executor.setMaxPoolSize(5);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        //配置队列大小
        executor.setQueueCapacity(Integer.MAX_VALUE);
        //配置线程池中的线程的名称前缀
        executor.setThreadFactory(threadFactory);
        // rejection-policy：当pool已经达到max size的时候，如何处理新任务
        // CALLER_RUNS：不在新线程中执行任务，而是有调用者所在的线程来执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        //执行初始化
        executor.initialize();
        return executor;
    }
}
