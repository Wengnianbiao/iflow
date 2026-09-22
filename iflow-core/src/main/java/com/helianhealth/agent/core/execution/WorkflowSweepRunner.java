package com.helianhealth.agent.core.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 周期性扫描仍处于 RUNNING 的工作流，把已经超过执行期限的实例交给 {@link WorkflowSweeper} 终止。
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
            @Value("${iflow.workflow-execution.sweeper.enabled:true}") boolean enabled,
            @Value("${iflow.workflow-execution.sweeper.interval-seconds:30}")
                    long intervalSeconds) {
        this.workflowSweeper = workflowSweeper;
        this.workflowExecutor = workflowExecutor;
        this.enabled = enabled;
        long seconds = intervalSeconds > 0 ? intervalSeconds : 30L;
        this.intervalMillis = seconds * 1000L;
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            LOGGER.info("Workflow sweeper is disabled");
            return;
        }
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
                workflowSweeper.sweepRunningWorkflows(workflowExecutor);
            } catch (RuntimeException e) {
                LOGGER.error("Workflow sweep failed", e);
            }
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
