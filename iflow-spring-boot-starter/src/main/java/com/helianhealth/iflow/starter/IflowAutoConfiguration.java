package com.helianhealth.iflow.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.ai.mcp.McpServerConfiguration;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.utils.SpringContextHolder;
import com.helianhealth.iflow.core.config.IflowCoreConfiguration;
import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.gateway.rest.GatewayConfiguration;
import com.helianhealth.iflow.gateway.schedule.WorkflowScheduleConfiguration;
import com.helianhealth.iflow.persistence.mysql.config.MySqlRepositoryConfiguration;
import com.helianhealth.iflow.tasks.http.HttpTask;
import com.helianhealth.iflow.tasks.http.providers.DefaultRestTemplateProvider;
import com.helianhealth.iflow.tasks.http.providers.RestTemplateProvider;
import com.helianhealth.iflow.tasks.jdbc.JdbcTask;
import com.helianhealth.iflow.tasks.jdbc.providers.DefaultJdbcConnectionProvider;
import com.helianhealth.iflow.tasks.jdbc.providers.JdbcConnectionProvider;
import com.helianhealth.iflow.tasks.mapping.MappingTask;
import com.helianhealth.iflow.tasks.mapping.ParamMapTransformer;
import com.helianhealth.iflow.tasks.spi.SpiExtensionRouter;
import com.helianhealth.iflow.tasks.spi.SpiTask;
import com.helianhealth.iflow.tasks.spi.SpiTaskMapper;
import com.helianhealth.iflow.tasks.webservice.WebServiceTask;
import com.helianhealth.iflow.tasks.webservice.providers.DefaultSoapClientProvider;
import com.helianhealth.iflow.tasks.webservice.providers.SoapClientProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;

/** iFlow Starter 入口；宿主无需扫描 iFlow 包或导入内部配置。 */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, JacksonAutoConfiguration.class})
@ConditionalOnProperty(prefix = "iflow", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({IflowCoreConfiguration.class, MySqlRepositoryConfiguration.class,
        GatewayConfiguration.class, WorkflowScheduleConfiguration.class, McpServerConfiguration.class})
public class IflowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SpringContextHolder springContextHolder() {
        return new SpringContextHolder();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HttpTask.class)
    static class HttpTaskConfiguration {

        @Bean
        @ConditionalOnMissingBean(RestTemplateProvider.class)
        public DefaultRestTemplateProvider defaultRestTemplateProvider(
                ObjectMapper objectMapper,
                @Value("${iflow.tasks.http.read-timeout:30000ms}") Duration readTimeout,
                @Value("${iflow.tasks.http.connect-timeout:5000ms}") Duration connectTimeout) {
            return new DefaultRestTemplateProvider(objectMapper, readTimeout, connectTimeout);
        }

        @Bean(name = TaskType.TASK_TYPE_HTTP)
        @ConditionalOnMissingBean(name = TaskType.TASK_TYPE_HTTP)
        public HttpTask httpTask(RestTemplateProvider provider, ObjectMapper objectMapper) {
            return new HttpTask(provider, objectMapper);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTask.class)
    static class JdbcTaskConfiguration {

        @Bean
        @ConditionalOnMissingBean(JdbcConnectionProvider.class)
        public DefaultJdbcConnectionProvider defaultJdbcConnectionProvider() {
            return new DefaultJdbcConnectionProvider();
        }

        @Bean(name = TaskType.TASK_TYPE_JDBC)
        @ConditionalOnMissingBean(name = TaskType.TASK_TYPE_JDBC)
        public JdbcTask jdbcTask(JdbcConnectionProvider provider, ObjectMapper objectMapper) {
            return new JdbcTask(provider, objectMapper);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebServiceTask.class)
    static class WebServiceTaskConfiguration {

        @Bean
        @ConditionalOnMissingBean(SoapClientProvider.class)
        public DefaultSoapClientProvider defaultSoapClientProvider() {
            return new DefaultSoapClientProvider();
        }

        @Bean(name = TaskType.TASK_TYPE_WEBSERVICE)
        @ConditionalOnMissingBean(name = TaskType.TASK_TYPE_WEBSERVICE)
        public WebServiceTask webServiceTask(SoapClientProvider provider, ObjectMapper objectMapper) {
            return new WebServiceTask(provider, objectMapper);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MappingTask.class)
    static class MappingTaskConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ParamMapTransformer paramMapTransformer(GraalJSEvaluator evaluator) {
            return new ParamMapTransformer(evaluator);
        }

        @Bean(name = TaskType.TASK_TYPE_MAPPING)
        @ConditionalOnMissingBean(name = TaskType.TASK_TYPE_MAPPING)
        public MappingTask mappingTask(ParamMapTransformer transformer, ObjectMapper objectMapper) {
            return new MappingTask(transformer, objectMapper);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SpiTask.class)
    @Import(SpiTaskMapper.class)
    static class SpiTaskConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SpiExtensionRouter spiExtensionRouter() {
            return new SpiExtensionRouter();
        }

        @Bean(name = TaskType.TASK_TYPE_SPI)
        @ConditionalOnMissingBean(name = TaskType.TASK_TYPE_SPI)
        public SpiTask spiTask(SpiExtensionRouter router) {
            return new SpiTask(router);
        }
    }
}
