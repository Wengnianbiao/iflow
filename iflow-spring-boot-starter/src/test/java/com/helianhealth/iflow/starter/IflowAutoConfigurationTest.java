package com.helianhealth.iflow.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.ai.mcp.McpServerEndpoint;
import com.helianhealth.iflow.ai.mcp.McpToolRegistry;
import com.helianhealth.iflow.common.utils.SpringContextHolder;
import com.helianhealth.iflow.console.controller.WorkflowDefinitionController;
import com.helianhealth.iflow.console.controller.WorkflowExecutionController;
import com.helianhealth.iflow.core.config.IflowCoreConfiguration;
import com.helianhealth.iflow.core.config.IflowProperties;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.dao.IndexDAO;
import com.helianhealth.iflow.core.dao.MetadataDAO;
import com.helianhealth.iflow.core.dao.QueueDAO;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.lock.Lock;
import com.helianhealth.iflow.core.queue.ExecutionQueue;
import com.helianhealth.iflow.gateway.rest.OpenApiController;
import com.helianhealth.iflow.persistence.mysql.dao.MySqlExecutionDAO;
import com.helianhealth.iflow.persistence.mysql.dao.MySqlIndexDAO;
import com.helianhealth.iflow.persistence.mysql.dao.MySqlMetadataDAO;
import com.helianhealth.iflow.tasks.http.HttpTask;
import com.helianhealth.iflow.tasks.http.providers.DefaultRestTemplateProvider;
import com.helianhealth.iflow.tasks.http.providers.RestTemplateProvider;
import com.helianhealth.iflow.tasks.jdbc.JdbcTask;
import com.helianhealth.iflow.tasks.jdbc.providers.DefaultJdbcConnectionProvider;
import com.helianhealth.iflow.tasks.jdbc.providers.JdbcConnectionProvider;
import com.helianhealth.iflow.tasks.mapping.MappingTask;
import com.helianhealth.iflow.tasks.spi.SpiTask;
import com.helianhealth.iflow.tasks.webservice.WebServiceTask;
import com.helianhealth.iflow.tasks.webservice.providers.DefaultSoapClientProvider;
import com.helianhealth.iflow.tasks.webservice.providers.SoapClientProvider;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IflowAutoConfigurationTest {

    private static final String[] PROPERTIES = {
            "agent.repository.vendor=mysql",
            "spring.datasource.url=jdbc:h2:mem:starter;MODE=MySQL",
            "iflow.workflow-execution.sweeper.enabled=false",
            "iflow.workflow-execution.task-thread-count=1"
    };

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withAllowBeanDefinitionOverriding(false)
            .withAllowCircularReferences(false)
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(HostApplication.class)
            .withPropertyValues(PROPERTIES);

    @Test
    void discoversCompleteStarterWithoutScanningIflowPackages() {
        runner.run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(WorkflowExecutor.class)
                    .hasSingleBean(MySqlMetadataDAO.class)
                    .hasSingleBean(MySqlExecutionDAO.class)
                    .hasSingleBean(MySqlIndexDAO.class)
                    .hasSingleBean(OpenApiController.class)
                    .hasSingleBean(WorkflowDefinitionController.class)
                    .hasSingleBean(WorkflowExecutionController.class)
                    .hasSingleBean(McpServerEndpoint.class)
                    .hasSingleBean(SpringContextHolder.class)
                    .doesNotHaveBean(UnrelatedComponent.class);
            SystemTaskRegistry registry = context.getBean(SystemTaskRegistry.class);
            assertThat(registry.get("HTTP")).isInstanceOf(HttpTask.class);
            assertThat(registry.get("JDBC")).isInstanceOf(JdbcTask.class);
            assertThat(registry.get("WEBSERVICE")).isInstanceOf(WebServiceTask.class);
            assertThat(registry.get("MAPPING")).isInstanceOf(MappingTask.class);
            assertThat(registry.get("SPI")).isInstanceOf(SpiTask.class);
            assertThat(registry.isSystemTask("DO_WHILE")).isTrue();
            Map<?, ?> mappers = context.getBean(IflowCoreConfiguration.TASK_MAPPERS_BY_TASK_TYPE, Map.class);
            assertThat(mappers.get("SPI")).isInstanceOf(TaskMapper.class);
            assertThat(context.getBean(McpToolRegistry.class).listTools()).hasSize(1);
        });
    }

    @Test
    void globalSwitchDisablesEngineTasksAndWebEndpoints() {
        runner.withPropertyValues("iflow.enabled=false").run(context ->
                assertThat(context).hasNotFailed()
                        .doesNotHaveBean(WorkflowExecutor.class)
                        .doesNotHaveBean(WorkflowSystemTask.class)
                        .doesNotHaveBean(MetadataDAO.class)
                        .doesNotHaveBean(OpenApiController.class)
                        .doesNotHaveBean(WorkflowDefinitionController.class)
                        .doesNotHaveBean(McpServerEndpoint.class)
                        .doesNotHaveBean(McpToolRegistry.class)
                        .doesNotHaveBean(SpringContextHolder.class));
    }

    @Test
    void consoleSwitchKeepsEngineGatewayAndMcpAvailable() {
        runner.withPropertyValues("agent.console.enabled=false").run(context ->
                assertThat(context).hasNotFailed()
                        .hasSingleBean(WorkflowExecutor.class)
                        .hasSingleBean(OpenApiController.class)
                        .hasSingleBean(McpServerEndpoint.class)
                        .doesNotHaveBean(WorkflowDefinitionController.class)
                        .doesNotHaveBean(WorkflowExecutionController.class));
    }

    @Test
    void mcpSwitchDisablesItsInfrastructure() {
        runner.withPropertyValues("iflow.mcp.enabled=false").run(context ->
                assertThat(context).hasNotFailed()
                        .hasSingleBean(WorkflowExecutor.class)
                        .hasSingleBean(WorkflowDefinitionController.class)
                        .doesNotHaveBean(McpServerEndpoint.class)
                        .doesNotHaveBean(McpToolRegistry.class));
    }

    @Test
    void nonWebHostOnlyStartsEngineAndTasks() {
        new ApplicationContextRunner()
                .withAllowBeanDefinitionOverriding(false)
                .withAllowCircularReferences(false)
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(HostApplication.class)
                .withPropertyValues(PROPERTIES).run(context ->
                        assertThat(context).hasNotFailed()
                                .hasSingleBean(WorkflowExecutor.class)
                                .hasSingleBean(HttpTask.class)
                                .doesNotHaveBean(OpenApiController.class)
                                .doesNotHaveBean(WorkflowDefinitionController.class)
                                .doesNotHaveBean(McpToolRegistry.class));
    }

    @Test
    void usesHostDataSourceAndObjectMapper() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:host");
        ObjectMapper objectMapper = new ObjectMapper();
        runner.withBean(DataSource.class, () -> dataSource)
                .withBean(ObjectMapper.class, () -> objectMapper).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DataSource.class)
                            .hasSingleBean(ObjectMapper.class).hasSingleBean(WorkflowExecutor.class);
                    assertThat(context.getBean(DataSource.class)).isSameAs(dataSource);
                    assertThat(context.getBean(ObjectMapper.class)).isSameAs(objectMapper);
                });
    }

    @Test
    void backsOffForHostProvidersQueueAndLock() {
        RestTemplateProvider http = mock(RestTemplateProvider.class);
        JdbcConnectionProvider jdbc = mock(JdbcConnectionProvider.class);
        SoapClientProvider soap = mock(SoapClientProvider.class);
        QueueDAO queueDAO = mock(QueueDAO.class);
        ExecutionQueue queue = mock(ExecutionQueue.class);
        Lock lock = mock(Lock.class);
        Executor executor = mock(Executor.class);
        runner.withBean(RestTemplateProvider.class, () -> http)
                .withBean(JdbcConnectionProvider.class, () -> jdbc)
                .withBean(SoapClientProvider.class, () -> soap)
                .withBean(QueueDAO.class, () -> queueDAO)
                .withBean(ExecutionQueue.class, () -> queue)
                .withBean(Lock.class, () -> lock)
                .withBean(IflowCoreConfiguration.ASYNC_TASK_EXECUTOR, Executor.class, () -> executor)
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(RestTemplateProvider.class)
                            .hasSingleBean(JdbcConnectionProvider.class)
                            .hasSingleBean(SoapClientProvider.class)
                            .hasSingleBean(QueueDAO.class)
                            .hasSingleBean(ExecutionQueue.class)
                            .hasSingleBean(Lock.class)
                            .doesNotHaveBean(DefaultRestTemplateProvider.class)
                            .doesNotHaveBean(DefaultJdbcConnectionProvider.class)
                            .doesNotHaveBean(DefaultSoapClientProvider.class);
                    assertThat(context.getBean(ExecutionQueue.class)).isSameAs(queue);
                    assertThat(context.getBean(Lock.class)).isSameAs(lock);
                    assertThat(context.getBean(IflowCoreConfiguration.ASYNC_TASK_EXECUTOR)).isSameAs(executor);
                });
    }

    @Test
    void namedTaskBeanReplacesDefaultTaskInRegistry() {
        WorkflowSystemTask task = new WorkflowSystemTask("HTTP") {};
        runner.withBean("HTTP", WorkflowSystemTask.class, () -> task).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(HttpTask.class);
            assertThat(context.getBean(SystemTaskRegistry.class).get("HTTP")).isSameAs(task);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "jdbc", "webservice", "mapping", "spi"})
    void excludedTaskModuleDoesNotBreakEngineStartup(String module) {
        runner.withClassLoader(new FilteredClassLoader("com.helianhealth.iflow.tasks." + module))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkflowExecutor.class);
                    assertThat(context.getBean(SystemTaskRegistry.class)
                            .isSystemTask(module.toUpperCase(Locale.ROOT))).isFalse();
                    assertThat(context.getBean(SystemTaskRegistry.class).isSystemTask("INLINE")).isTrue();
                });
    }

    @Test
    void bindsEnginePropertiesAndHttpDurations() {
        runner.withPropertyValues("iflow.workflow-execution.default-timeout-seconds=90",
                "iflow.tasks.http.read-timeout=2s", "iflow.tasks.http.connect-timeout=500ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IflowProperties.class).getWorkflowExecution()
                            .getDefaultTimeoutSeconds()).isEqualTo(90);
                });
    }

    @Test
    void hostDaosReplaceMySqlDefaults() {
        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        ExecutionDAO executionDAO = mock(ExecutionDAO.class);
        IndexDAO indexDAO = mock(IndexDAO.class);
        runner.withBean(MetadataDAO.class, () -> metadataDAO)
                .withBean(ExecutionDAO.class, () -> executionDAO)
                .withBean(IndexDAO.class, () -> indexDAO).run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(MetadataDAO.class).hasSingleBean(ExecutionDAO.class)
                            .hasSingleBean(IndexDAO.class)
                            .doesNotHaveBean(MySqlMetadataDAO.class)
                            .doesNotHaveBean(MySqlExecutionDAO.class)
                            .doesNotHaveBean(MySqlIndexDAO.class);
                    assertThat(context.getBean(MetadataDAO.class)).isSameAs(metadataDAO);
                    assertThat(context.getBean(ExecutionDAO.class)).isSameAs(executionDAO);
                    assertThat(context.getBean(IndexDAO.class)).isSameAs(indexDAO);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class HostApplication {
    }

    @Component
    static class UnrelatedComponent {
    }
}
