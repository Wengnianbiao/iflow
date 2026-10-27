package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.config.IflowProperties;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class WorkflowSweepRunnerTest {
    @Test
    void cleansUpSynchronouslyDuringStartupBeforePeriodicSweeps() {
        WorkflowSweeper sweeper = mock(WorkflowSweeper.class);
        WorkflowExecutor executor = mock(WorkflowExecutor.class);
        IflowProperties properties = new IflowProperties();
        properties.getWorkflowExecution().getSweeper().setIntervalSeconds(3600);
        Thread startupThread = Thread.currentThread();
        doAnswer(invocation -> {
            if (Thread.currentThread() == startupThread) {
                verify(sweeper, times(1)).sweepRunningWorkflows(executor);
            }
            return null;
        }).when(sweeper).sweepRunningWorkflows(executor);
        WorkflowSweepRunner runner = new WorkflowSweepRunner(sweeper, executor, properties);
        try {
            runner.afterPropertiesSet();
            verify(sweeper, atLeastOnce()).sweepRunningWorkflows(executor);
        } finally {
            runner.destroy();
        }
    }

    @Test
    void explicitlyDisablingSweeperDisablesStartupAndPeriodicPasses() {
        WorkflowSweeper sweeper = mock(WorkflowSweeper.class);
        IflowProperties properties = new IflowProperties();
        properties.getWorkflowExecution().getSweeper().setEnabled(false);
        WorkflowSweepRunner runner = new WorkflowSweepRunner(sweeper, mock(WorkflowExecutor.class), properties);
        runner.afterPropertiesSet();
        runner.destroy();
        verifyNoInteractions(sweeper);
    }
}
