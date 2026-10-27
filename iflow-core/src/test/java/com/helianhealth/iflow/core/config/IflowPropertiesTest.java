package com.helianhealth.iflow.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IflowPropertiesTest {
    @Test
    void taskDefaultsDoNotRetryOrDelayTasks() {
        var defaults = new IflowProperties().getTaskExecution();
        assertEquals(0, defaults.getDefaultRetryCount());
        assertEquals(0, defaults.getDefaultRetryDelaySeconds());
        assertEquals(WorkflowTask.RetryLogic.FIXED, defaults.getDefaultRetryLogic());
        assertEquals(0, defaults.getDefaultStartDelay());
    }

    @Test
    void bindsAllTaskPolicyDefaults() {
        var defaults = new Binder(new MapConfigurationPropertySource(Map.of(
                "iflow.task-execution.default-retry-count", "3",
                "iflow.task-execution.default-retry-delay-seconds", "1",
                "iflow.task-execution.default-retry-logic", "EXPONENTIAL_BACKOFF",
                "iflow.task-execution.default-start-delay", "2")))
                .bind("iflow", IflowProperties.class).get().getTaskExecution();
        assertEquals(3, defaults.getDefaultRetryCount());
        assertEquals(1, defaults.getDefaultRetryDelaySeconds());
        assertEquals(WorkflowTask.RetryLogic.EXPONENTIAL_BACKOFF, defaults.getDefaultRetryLogic());
        assertEquals(2, defaults.getDefaultStartDelay());
    }

    @Test
    void rejectsInvalidTaskPolicyConfigurationDuringBinding() {
        for (String key : List.of("default-retry-count", "default-retry-delay-seconds",
                "default-start-delay")) {
            for (String value : List.of("-1", "0.5", "invalid")) {
                var binder = new Binder(new MapConfigurationPropertySource(Map.of("iflow.task-execution." + key, value)));
                assertThrows(org.springframework.boot.context.properties.bind.BindException.class,
                        () -> binder.bind("iflow", IflowProperties.class));
            }
        }
        for (String key : List.of("default-retry-logic")) {
            var binder = new Binder(new MapConfigurationPropertySource(Map.of("iflow.task-execution." + key, "UNKNOWN")));
            assertThrows(org.springframework.boot.context.properties.bind.BindException.class,
                    () -> binder.bind("iflow", IflowProperties.class));
        }
    }

    @Test
    void defaultsProvideSixtySecondsAndOneSecondSweeps() {
        var execution = new IflowProperties().getWorkflowExecution();
        assertEquals(60, execution.getDefaultTimeoutSeconds());
        assertTrue(execution.getSweeper().isEnabled());
        assertEquals(1, execution.getSweeper().getIntervalSeconds());
    }

    @Test
    void bindsExistingConfigurationKeys() {
        IflowProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                "iflow.enabled", "false",
                "iflow.workflow-execution.default-timeout-seconds", "120",
                "iflow.workflow-execution.sweeper.enabled", "false",
                "iflow.workflow-execution.sweeper.interval-seconds", "5")))
                .bind("iflow", IflowProperties.class).get();
        assertFalse(properties.isEnabled());
        assertEquals(120, properties.getWorkflowExecution().getDefaultTimeoutSeconds());
        assertFalse(properties.getWorkflowExecution().getSweeper().isEnabled());
        assertEquals(5, properties.getWorkflowExecution().getSweeper().getIntervalSeconds());
    }

    @Test
    void rejectsDisabledBudgetAndInvalidSweepIntervals() {
        IflowProperties properties = new IflowProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.getWorkflowExecution().setDefaultTimeoutSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> properties.getWorkflowExecution().setDefaultTimeoutSeconds(-1));
        assertThrows(IllegalArgumentException.class, () -> properties.getWorkflowExecution().getSweeper().setIntervalSeconds(0));
    }
}
