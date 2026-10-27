package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeciderServiceTimeoutTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeciderService decider = new DeciderService(new IDGenerator(), new ParametersUtils(mapper),
            new SystemTaskRegistry(Set.of()), Map.of());

    @Test
    void runtimeBudgetExpiresAtSixtySeconds() {
        WorkflowModel workflow = workflow();
        workflow.setStartTime(System.currentTimeMillis() - 59_000);
        assertDoesNotThrow(() -> decider.checkWorkflowTimeout(workflow));
        workflow.setStartTime(System.currentTimeMillis() - 60_000);
        var error = assertThrows(TerminateWorkflowException.class, () -> decider.checkWorkflowTimeout(workflow));
        assertEquals(WorkflowModel.Status.TIMED_OUT, error.getWorkflowStatus());
        assertTrue(error.getMessage().contains("超时配置为 60 秒"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void checksDoNotResolveUnconfiguredRuntimeBudgets(long seconds) {
        WorkflowModel workflow = workflow();
        workflow.getWorkflowDefinition().setTimeoutSeconds(seconds);
        workflow.setStartTime(System.currentTimeMillis() - 120_000);
        assertDoesNotThrow(() -> decider.checkWorkflowTimeout(workflow));
        assertEquals(seconds, workflow.getWorkflowDefinition().getTimeoutSeconds());
    }

    @Test
    void usesRuntimeOverrideAndRetryTimeWithoutOverflow() {
        WorkflowModel workflow = workflow();
        workflow.getWorkflowDefinition().setTimeoutSeconds(120);
        workflow.setStartTime(System.currentTimeMillis() - 60_000);
        assertDoesNotThrow(() -> decider.checkWorkflowTimeout(workflow));
        workflow.getWorkflowDefinition().setTimeoutSeconds(30);
        assertThrows(TerminateWorkflowException.class, () -> decider.checkWorkflowTimeout(workflow));
        workflow.setLastRetriedTime(System.currentTimeMillis());
        assertDoesNotThrow(() -> decider.checkWorkflowTimeout(workflow));
        workflow.setLastRetriedTime(System.currentTimeMillis() - 30_000);
        assertThrows(TerminateWorkflowException.class, () -> decider.checkWorkflowTimeout(workflow));
        workflow.getWorkflowDefinition().setTimeoutSeconds(Long.MAX_VALUE);
        assertDoesNotThrow(() -> decider.checkWorkflowTimeout(workflow));
    }

    @Test
    void alertOnlyKeepsRunningAcrossRepeatedChecksAndSweeps() {
        WorkflowModel workflow = workflow();
        workflow.getWorkflowDefinition().setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
        workflow.setStartTime(System.currentTimeMillis() - 60_000);
        assertDoesNotThrow(() -> decider.checkWorkflowTimeout(workflow));
        assertDoesNotThrow(() -> decider.checkWorkflowTimeout(workflow));
        WorkflowExecutor executor = mock(WorkflowExecutor.class);
        assertFalse(new WorkflowSweeper(new SystemTaskRegistry(Set.of()), null, null, decider, 0)
                .sweep(workflow, executor));
        verifyNoInteractions(executor);
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertEquals(0, workflow.getEndTime());
    }

    @Test
    void expiredRunningDecisionsNeverMapInitialTasks() {
        ParametersUtils parameters = mock(ParametersUtils.class);
        DeciderService decider = new DeciderService(new IDGenerator(), parameters,
                new SystemTaskRegistry(Set.of()), Map.of());
        WorkflowModel workflow = workflow();
        workflow.setStartTime(System.currentTimeMillis() - 60_000);
        assertThrows(TerminateWorkflowException.class, () -> decider.decide(workflow));
        verifyNoInteractions(parameters);
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowModel.Status.class, names = "RUNNING", mode = EnumSource.Mode.EXCLUDE)
    void endedDecisionsDoNotMapOrCheckTimeout(WorkflowModel.Status status) {
        ParametersUtils parameters = mock(ParametersUtils.class);
        DeciderService decider = new DeciderService(new IDGenerator(), parameters,
                new SystemTaskRegistry(Set.of()), Map.of());
        WorkflowModel workflow = workflow();
        workflow.setStartTime(System.currentTimeMillis() - 120_000);
        workflow.setStatus(status);
        assertTrue(decider.decide(workflow).tasksToBeScheduled.isEmpty());
        assertEquals(status, workflow.getStatus());
        verifyNoInteractions(parameters);
    }

    @Test
    void terminalUnstartedAndMissingDefinitionsAreIgnored() {
        WorkflowModel workflow = workflow();
        workflow.setStartTime(0);
        assertDoesNotThrow(() -> decider.checkWorkflowTimeout(workflow));
        workflow.setStartTime(System.currentTimeMillis() - 120_000);
        workflow.setStatus(WorkflowModel.Status.INTERRUPTED);
        assertDoesNotThrow(() -> decider.checkWorkflowTimeout(workflow));
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setWorkflowDefinition(null);
        assertDoesNotThrow(() -> decider.checkWorkflowTimeout(workflow));
    }

    private static WorkflowModel workflow() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("timeout-test");
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("test").timeoutSeconds(60).build());
        return workflow;
    }
}
