package com.helianhealth.iflow.core.config;

import com.helianhealth.iflow.core.dao.QueueDAO;
import com.helianhealth.iflow.core.queue.ExecutionQueue;
import com.helianhealth.iflow.core.queue.InMemoryExecutionQueue;
import com.helianhealth.iflow.core.queue.InMemoryQueueDAO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

/** 未配置队列类型时默认使用内存模式；其他类型由对应扩展模块提供实现。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iflow.queue", name = "type", havingValue = "memory", matchIfMissing = true)
public class InMemoryQueueConfiguration {

    @Bean
    public QueueDAO inMemoryQueueDAO() {
        return new InMemoryQueueDAO();
    }

    @Bean
    public ExecutionQueue inMemoryExecutionQueue(
            QueueDAO queueDAO,
            @Qualifier(AgentCoreConfiguration.ASYNC_TASK_EXECUTOR) Executor taskExecutor) {
        return new InMemoryExecutionQueue(queueDAO, taskExecutor);
    }
}
