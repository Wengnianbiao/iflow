package com.helianhealth.iflow.core.config;

import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.DeciderService;
import com.helianhealth.iflow.core.execution.WorkflowCompletionRegistry;
import com.helianhealth.iflow.core.execution.WorkflowExecutorOps;
import com.helianhealth.iflow.core.execution.WorkflowSweepRunner;
import com.helianhealth.iflow.core.execution.WorkflowSweeper;
import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.core.execution.evaluators.ValueParamEvaluator;
import com.helianhealth.iflow.core.execution.mapper.DoWhileTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.ExclusiveJoinTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.ForkJoinTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.HttpTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.InlineTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.JdbcTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.JoinTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.NoopTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.ParamMappingTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.SetKeywordTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.SwitchTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TerminateTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.WebServiceTaskMapper;
import com.helianhealth.iflow.core.execution.tasks.DoWhile;
import com.helianhealth.iflow.core.execution.tasks.ExclusiveJoin;
import com.helianhealth.iflow.core.execution.tasks.Fork;
import com.helianhealth.iflow.core.execution.tasks.Inline;
import com.helianhealth.iflow.core.execution.tasks.Join;
import com.helianhealth.iflow.core.execution.tasks.Noop;
import com.helianhealth.iflow.core.execution.tasks.SetKeyword;
import com.helianhealth.iflow.core.execution.tasks.Switch;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.Terminate;
import com.helianhealth.iflow.core.lock.ExecutionLockService;
import com.helianhealth.iflow.core.service.MetadataServiceImpl;
import com.helianhealth.iflow.core.service.WorkflowExecutionServiceImpl;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IflowProperties.class)
@Import({
        ExecutionDAOFacade.class,
        DeciderService.class,
        WorkflowCompletionRegistry.class,
        WorkflowExecutorOps.class,
        WorkflowSweepRunner.class,
        WorkflowSweeper.class,
        GraalJSEvaluator.class,
        ValueParamEvaluator.class,
        DoWhileTaskMapper.class,
        ExclusiveJoinTaskMapper.class,
        ForkJoinTaskMapper.class,
        HttpTaskMapper.class,
        InlineTaskMapper.class,
        JdbcTaskMapper.class,
        JoinTaskMapper.class,
        NoopTaskMapper.class,
        ParamMappingTaskMapper.class,
        SetKeywordTaskMapper.class,
        SwitchTaskMapper.class,
        TerminateTaskMapper.class,
        WebServiceTaskMapper.class,
        DoWhile.class,
        ExclusiveJoin.class,
        Fork.class,
        Inline.class,
        Join.class,
        Noop.class,
        SetKeyword.class,
        Switch.class,
        SystemTaskRegistry.class,
        Terminate.class,
        ExecutionLockService.class,
        MetadataServiceImpl.class,
        WorkflowExecutionServiceImpl.class,
        IDGenerator.class,
        ParametersUtils.class,
        InMemoryQueueConfiguration.class,
        InMemoryLockConfiguration.class
})
public class IflowCoreConfiguration {

    public static final String TASK_MAPPERS_BY_TASK_TYPE = "taskMappersByTaskType";
    public static final String ASYNC_TASK_EXECUTOR = "asyncTaskExecutor";

    @Bean
    @ConditionalOnMissingBean(name = TASK_MAPPERS_BY_TASK_TYPE)
    @Qualifier(TASK_MAPPERS_BY_TASK_TYPE)
    public Map<String, TaskMapper> taskMappersByTaskType(List<TaskMapper> taskMappers) {
        return taskMappers.stream()
                .collect(toMap(TaskMapper::getTaskType, identity(), (a, b) -> a, HashMap::new));
    }

    /** 异步系统任务使用有界内存队列；MAPPING 和控制节点仍由当前决策线程执行。 */
    @Bean(name = ASYNC_TASK_EXECUTOR)
    @ConditionalOnMissingBean(name = ASYNC_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor asyncTaskExecutor(
            @Value(
                            "${iflow.workflow-execution.task-thread-count:${iflow.workflow-execution.fork-thread-count:32}}")
                    int threadCount,
            @Value(
                            "${iflow.workflow-execution.task-queue-capacity:${iflow.workflow-execution.fork-queue-capacity:256}}")
                    int queueCapacity) {
        if (threadCount <= 0) {
            throw new IllegalArgumentException("async task thread count must be positive");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException(
                    "async task queue capacity must not be negative");
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadCount);
        executor.setMaxPoolSize(threadCount);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("iflow-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }
}
