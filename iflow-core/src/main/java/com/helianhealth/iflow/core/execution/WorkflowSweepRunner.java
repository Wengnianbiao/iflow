package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.config.IflowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动时清理进程遗留实例，随后周期性检查运行实例的超时。
 */
@Component
public class WorkflowSweepRunner implements InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowSweepRunner.class);

    private final WorkflowSweeper workflowSweeper;
    private final WorkflowExecutor workflowExecutor;
    private final boolean enabled;
    private final long intervalMillis;
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread thread;

    public WorkflowSweepRunner(
            WorkflowSweeper workflowSweeper,
            WorkflowExecutor workflowExecutor,
            IflowProperties properties) {
        this.workflowSweeper = workflowSweeper;
        this.workflowExecutor = workflowExecutor;
        this.enabled = properties.getWorkflowExecution().getSweeper().isEnabled();
        this.intervalMillis = properties.getWorkflowExecution().getSweeper().getIntervalSeconds() * 1000L;
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            LOGGER.info("Workflow sweeper is disabled");
            return;
        }
        sweep();
        running.set(true);
        thread = new Thread(this::loop, "iflow-workflow-sweeper");
        thread.setDaemon(true);
        thread.start();
        LOGGER.info("Workflow sweeper started, interval {} ms", intervalMillis);
    }

    @Override
    public void destroy() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void loop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (running.get()) {
                sweep();
            }
        }
    }

    private void sweep() {
        try {
            workflowSweeper.sweepRunningWorkflows(workflowExecutor);
        } catch (RuntimeException error) {
            LOGGER.error("Workflow sweep failed", error);
        }
    }
}
