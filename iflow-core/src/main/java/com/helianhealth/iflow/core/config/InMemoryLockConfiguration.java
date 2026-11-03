package com.helianhealth.iflow.core.config;

import com.helianhealth.iflow.core.lock.InMemoryLock;
import com.helianhealth.iflow.core.lock.Lock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 未配置执行锁类型时默认使用内存锁；其他类型由对应扩展模块提供实现。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iflow.workflow-execution-lock", name = "type", havingValue = "memory", matchIfMissing = true)
public class InMemoryLockConfiguration {

    @Bean
    public Lock inMemoryLock() {
        return new InMemoryLock();
    }
}
