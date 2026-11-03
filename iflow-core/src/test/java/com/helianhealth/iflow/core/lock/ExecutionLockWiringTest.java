package com.helianhealth.iflow.core.lock;

import com.helianhealth.iflow.core.config.InMemoryLockConfiguration;
import com.helianhealth.iflow.core.config.IflowProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionLockWiringTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "memory")
    void selectsMemoryWhenTypeIsMissingOrExplicitlyMemory(String type) {
        try (AnnotationConfigApplicationContext context = context(type)) {
            context.register(InMemoryLockConfiguration.class, ExecutionLockService.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(Lock.class).size());
            assertEquals(1, context.getBeansOfType(ExecutionLockService.class).size());
            assertInstanceOf(InMemoryLock.class, context.getBean(Lock.class));
            ExecutionLockService service = context.getBean(ExecutionLockService.class);
            assertTrue(service.acquireLock("workflow"));
            service.releaseLock("workflow");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replacesOnlyTheLockRegardlessOfRegistrationOrder(boolean customFirst) {
        Lock customLock = mock(Lock.class);
        when(customLock.acquireLock("workflow", 500, 60_000, TimeUnit.MILLISECONDS)).thenReturn(false, true);
        try (AnnotationConfigApplicationContext context = context("custom")) {
            if (customFirst) {
                context.registerBean("customLock", Lock.class, () -> customLock);
                context.register(InMemoryLockConfiguration.class);
            } else {
                context.register(InMemoryLockConfiguration.class);
                context.registerBean("customLock", Lock.class, () -> customLock);
            }
            context.register(ExecutionLockService.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(Lock.class).size());
            assertSame(customLock, context.getBean(Lock.class));
            ExecutionLockService service = context.getBean(ExecutionLockService.class);
            assertEquals(ExecutionLockService.class, service.getClass());
            assertFalse(service.acquireLock("workflow"));
            assertTrue(service.acquireLock("workflow"));
            service.waitForLock("workflow");
            service.releaseLock("workflow");
            verify(customLock).acquireLock("workflow", 60_000, TimeUnit.MILLISECONDS);
            verify(customLock).releaseLock("workflow");
        }
    }

    @Test
    void appliesConfiguredLeaseToBothTimedAndBlockingAcquisitions() {
        IflowProperties properties = new IflowProperties();
        properties.setLockTimeToTry(Duration.ofMillis(25));
        properties.setLockLeaseTime(Duration.ofSeconds(2));
        Lock customLock = mock(Lock.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(IflowProperties.class, () -> properties);
            context.registerBean(Lock.class, () -> customLock);
            context.register(ExecutionLockService.class);
            context.refresh();

            ExecutionLockService service = context.getBean(ExecutionLockService.class);
            service.acquireLock("workflow");
            service.waitForLock("workflow");
            verify(customLock).acquireLock("workflow", 25, 2_000, TimeUnit.MILLISECONDS);
            verify(customLock).acquireLock("workflow", 2_000, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void missingCustomImplementationFailsInsteadOfFallingBackToMemory() {
        try (AnnotationConfigApplicationContext context = context("custom")) {
            context.register(InMemoryLockConfiguration.class, ExecutionLockService.class);
            assertThrows(UnsatisfiedDependencyException.class, context::refresh);
            assertTrue(context.getDefaultListableBeanFactory().getBeansOfType(Lock.class).isEmpty());
        }
    }

    private static AnnotationConfigApplicationContext context(String type) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(IflowProperties.class);
        if (type != null) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("lock-type", Map.of("iflow.workflow-execution-lock.type", type)));
        }
        return context;
    }
}
