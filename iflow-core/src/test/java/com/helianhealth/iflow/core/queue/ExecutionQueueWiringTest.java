package com.helianhealth.iflow.core.queue;

import com.helianhealth.iflow.core.config.IflowCoreConfiguration;
import com.helianhealth.iflow.core.config.InMemoryQueueConfiguration;
import com.helianhealth.iflow.core.dao.QueueDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionQueueWiringTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "memory")
    void selectsMemoryWhenTypeIsMissingOrExplicitlyMemory(String type) {
        try (AnnotationConfigApplicationContext context = context(type)) {
            context.register(InMemoryQueueConfiguration.class, QueueClient.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(ExecutionQueue.class).size());
            assertEquals(1, context.getBeansOfType(QueueDAO.class).size());
            assertInstanceOf(InMemoryExecutionQueue.class, context.getBean(ExecutionQueue.class));
            assertInstanceOf(InMemoryQueueDAO.class, context.getBean(QueueDAO.class));
            assertSame(context.getBean(ExecutionQueue.class), context.getBean(QueueClient.class).queue);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selectsConfiguredCustomQueueRegardlessOfRegistrationOrder(boolean customFirst) {
        ExecutionQueue customQueue = (queueName, taskId, executeTask) -> true;
        QueueDAO customDAO = new InMemoryQueueDAO();
        try (AnnotationConfigApplicationContext context = context("custom")) {
            if (customFirst) {
                context.registerBean("customQueue", ExecutionQueue.class, () -> customQueue);
                context.register(InMemoryQueueConfiguration.class);
            } else {
                context.register(InMemoryQueueConfiguration.class);
                context.registerBean("customQueue", ExecutionQueue.class, () -> customQueue);
            }
            context.registerBean("customQueueDAO", QueueDAO.class, () -> customDAO);
            context.register(QueueClient.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(ExecutionQueue.class).size());
            assertEquals(1, context.getBeansOfType(QueueDAO.class).size());
            assertSame(customQueue, context.getBean(ExecutionQueue.class));
            assertSame(customDAO, context.getBean(QueueDAO.class));
            assertSame(customQueue, context.getBean(QueueClient.class).queue);
        }
    }

    @Test
    void doesNotSilentlyActivateMemoryWhenAnotherTypeIsConfigured() {
        try (AnnotationConfigApplicationContext context = context("custom")) {
            context.register(InMemoryQueueConfiguration.class);
            context.refresh();

            assertTrue(context.getBeansOfType(ExecutionQueue.class).isEmpty());
            assertTrue(context.getBeansOfType(QueueDAO.class).isEmpty());
        }
    }

    private static AnnotationConfigApplicationContext context(String type) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (type != null) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("queue-type", Map.of("iflow.queue.type", type)));
        }
        context.registerBean(IflowCoreConfiguration.ASYNC_TASK_EXECUTOR, Executor.class,
                () -> command -> { throw new AssertionError("wiring must not execute tasks"); });
        return context;
    }

    static class QueueClient {
        private final ExecutionQueue queue;

        QueueClient(ExecutionQueue queue) {
            this.queue = queue;
        }
    }
}
