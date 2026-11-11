package com.helianhealth.iflow.gateway.schedule;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowSchedule;
import com.helianhealth.iflow.persistence.mysql.dao.MySqlWorkflowScheduleDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 应用就绪后按固定间隔扫描到期计划。 */
public class WorkflowScheduleRunner implements DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowScheduleRunner.class);
    private static final int BATCH_SIZE = 100;

    private final ApplicationContext applicationContext;
    private final MySqlWorkflowScheduleDAO scheduleDAO;
    private final WorkflowScheduleService scheduleService;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "iflow-workflow-scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private boolean started;

    public WorkflowScheduleRunner(
            ApplicationContext applicationContext,
            MySqlWorkflowScheduleDAO scheduleDAO,
            WorkflowScheduleService scheduleService) {
        this.applicationContext = applicationContext;
        this.scheduleDAO = scheduleDAO;
        this.scheduleService = scheduleService;
    }

    @EventListener
    public synchronized void start(ApplicationReadyEvent event) {
        if (event.getApplicationContext() != applicationContext || started || executor.isShutdown()) {
            return;
        }
        Instant readyAt = Instant.now();
        executor.scheduleWithFixedDelay(() -> poll(readyAt), 0, 1, TimeUnit.SECONDS);
        started = true;
    }

    @Override
    public synchronized void destroy() {
        executor.shutdown();
    }

    private void poll(Instant readyAt) {
        try {
            for (WorkflowSchedule schedule : scheduleDAO.findDue(Instant.now(), BATCH_SIZE)) {
                if (executor.isShutdown()) {
                    return;
                }
                scheduleService.trigger(schedule, readyAt, Instant.now());
            }
        } catch (RuntimeException error) {
            LOGGER.error("Failed to scan due workflow schedules", error);
        }
    }
}
