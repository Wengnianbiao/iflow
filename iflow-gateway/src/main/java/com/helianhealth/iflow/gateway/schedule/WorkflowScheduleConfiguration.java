package com.helianhealth.iflow.gateway.schedule;

import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.service.WorkflowExecutionService;
import com.helianhealth.iflow.persistence.mysql.config.ConditionalOnMySqlRepository;
import com.helianhealth.iflow.persistence.mysql.dao.MySqlWorkflowScheduleDAO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/** 定时触发配置。 */
@ConditionalOnMySqlRepository
@ConditionalOnProperty(name = {"iflow.enabled", "iflow.scheduler.enabled"}, havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleConfiguration {

    @Bean
    public WorkflowScheduleService workflowScheduleService(
            MySqlWorkflowScheduleDAO scheduleDAO,
            MetadataService metadataService,
            WorkflowExecutionService workflowExecutionService) {
        return new WorkflowScheduleService(scheduleDAO, metadataService, workflowExecutionService);
    }

    @Bean
    public WorkflowScheduleRunner workflowScheduleRunner(
            ApplicationContext applicationContext,
            MySqlWorkflowScheduleDAO scheduleDAO,
            WorkflowScheduleService scheduleService) {
        return new WorkflowScheduleRunner(applicationContext, scheduleDAO, scheduleService);
    }
}
