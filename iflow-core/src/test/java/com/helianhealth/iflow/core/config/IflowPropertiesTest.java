package com.helianhealth.iflow.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IflowPropertiesTest {
    @Test
    void providesAndBindsExecutionLockDurations() {
        IflowProperties defaults = new IflowProperties();
        assertEquals(Duration.ofMillis(500), defaults.getLockTimeToTry());
        assertEquals(Duration.ofSeconds(60), defaults.getLockLeaseTime());

        IflowProperties configured = new Binder(new MapConfigurationPropertySource(Map.of(
                "iflow.lock-time-to-try", "100ms",
                "iflow.lock-lease-time", "2s")))
                .bind("iflow", IflowProperties.class).get();
        assertEquals(Duration.ofMillis(100), configured.getLockTimeToTry());
        assertEquals(Duration.ofSeconds(2), configured.getLockLeaseTime());
    }

    @Test
    void rejectsInvalidExecutionLockDurationsDuringBinding() {
        for (String value : List.of("-1ms", "invalid")) {
            var binder = new Binder(new MapConfigurationPropertySource(Map.of("iflow.lock-time-to-try", value)));
            assertThrows(org.springframework.boot.context.properties.bind.BindException.class,
                    () -> binder.bind("iflow", IflowProperties.class));
        }
        for (String value : List.of("0ms", "-1s", "PT0.000001S", "invalid")) {
            var binder = new Binder(new MapConfigurationPropertySource(Map.of("iflow.lock-lease-time", value)));
            assertThrows(org.springframework.boot.context.properties.bind.BindException.class,
                    () -> binder.bind("iflow", IflowProperties.class));
        }
        var configured = new Binder(new MapConfigurationPropertySource(Map.of("iflow.lock-time-to-try", "0ms")))
                .bind("iflow", IflowProperties.class).get();
        assertEquals(Duration.ZERO, configured.getLockTimeToTry());
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
