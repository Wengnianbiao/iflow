package com.helianhealth.iflow.console.config;

import com.helianhealth.iflow.console.controller.ExecutionStatisticsController;
import com.helianhealth.iflow.console.controller.WorkflowDefinitionController;
import com.helianhealth.iflow.console.controller.WorkflowExecutionController;
import com.helianhealth.iflow.console.controller.WorkflowScheduleController;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/** 控制台自动配置：工作流定义、执行查询与统计 API。 */
@AutoConfiguration(afterName = "com.helianhealth.iflow.starter.IflowAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(WorkflowExecutor.class)
@ConditionalOnProperty(name = {"iflow.enabled", "agent.console.enabled"}, havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ConsoleProperties.class)
@Import({WorkflowDefinitionController.class, WorkflowExecutionController.class,
        WorkflowScheduleController.class, ExecutionStatisticsController.class,
        ConsoleExceptionHandler.class})
public class ConsoleAutoConfiguration {
}
