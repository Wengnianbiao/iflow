package com.helianhealth.iflow.core.execution.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.config.AgentCoreConfiguration;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.execution.CallerAndForkTaskScheduler;
import com.helianhealth.iflow.core.execution.DeciderService;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.WorkflowExecutorOps;
import com.helianhealth.iflow.core.execution.mapper.DoWhileTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.NoopTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.lock.InMemoryExecutionLockService;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoWhileWiringTest {

    @Test
    void wiresWithoutCircularReferencesOrProxiesAndRegistersEachIteration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            context.register(TaskConfiguration.class, ParametersUtils.class, ExecutionDAOFacade.class,
                    IDGenerator.class, DoWhile.class, Noop.class, SystemTaskRegistry.class,
                    DeciderService.class, InMemoryExecutionLockService.class,
                    CallerAndForkTaskScheduler.class, WorkflowExecutorOps.class);
            context.refresh();

            DoWhile doWhile = context.getBean(DoWhile.class);
            DeciderService decider = context.getBean(DeciderService.class);
            WorkflowExecutor executor = context.getBean(WorkflowExecutor.class);
            ExecutionDAOFacade facade = context.getBean(ExecutionDAOFacade.class);
            assertEquals(DoWhile.class, doWhile.getClass());
            assertEquals(DeciderService.class, decider.getClass());
            assertEquals(WorkflowExecutorOps.class, executor.getClass());
            assertSame(doWhile, context.getBean(SystemTaskRegistry.class).get(TaskType.TASK_TYPE_DO_WHILE));

            WorkflowTask bodyDefinition = WorkflowTask.builder()
                    .name("body").taskReferenceName("body").type(TaskType.TASK_TYPE_NOOP).build();
            WorkflowTask loopDefinition = WorkflowTask.builder()
                    .name("loop").taskReferenceName("loop").type(TaskType.TASK_TYPE_DO_WHILE)
                    .loopOver(List.of(bodyDefinition)).loopItems("${workflow.input.items}").build();
            WorkflowDef definition = WorkflowDef.builder().route("spring-loop")
                    .tasks(List.of(loopDefinition)).build();
            WorkflowModel workflow = new WorkflowModel();
            workflow.setWorkflowId("spring-loop-instance");
            workflow.setWorkflowDefinition(definition);
            workflow.setInput(Map.of("items", List.of("first", "second")));

            facade.registerTasks(workflow, decider.getTasksToBeScheduled(workflow, loopDefinition, 0));
            TaskModel loop = workflow.getTaskByRefName("loop");
            assertNotNull(loop);
            loop.setStatus(TaskModel.Status.IN_PROGRESS);

            assertTrue(doWhile.execute(workflow, loop, executor));
            TaskModel first = workflow.getTaskByRefName("body__1");
            assertNotNull(first);
            assertEquals(TaskModel.Status.SCHEDULED, first.getStatus());
            assertEquals(0, first.getStartTime());
            assertEquals(1, first.getIteration());
            assertEquals(2, workflow.getTasks().size());
            assertFalse(doWhile.execute(workflow, loop, executor));
            assertEquals(2, workflow.getTasks().size());

            first.setStatus(TaskModel.Status.COMPLETED);
            assertTrue(doWhile.execute(workflow, loop, executor));
            TaskModel second = workflow.getTaskByRefName("body__2");
            assertNotNull(second);
            assertEquals(TaskModel.Status.SCHEDULED, second.getStatus());
            assertEquals(0, second.getStartTime());
            assertEquals(2, second.getIteration());
            assertEquals(3, workflow.getTasks().size());
            assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
            verify(context.getBean(ExecutionDAO.class), times(3)).createTasks(anyList());
            verify(context.getBean(ExecutionDAO.class), times(3)).updateWorkflow(workflow);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TaskConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MetadataService metadataService() {
            return mock(MetadataService.class);
        }

        @Bean(name = AgentCoreConfiguration.FORK_BRANCH_TASK_EXECUTOR)
        Executor forkBranchExecutor() {
            return command -> {
                throw new AssertionError("DO_WHILE 登记轮次任务时不应提交业务执行");
            };
        }

        @Bean
        ExecutionDAO executionDAO() {
            ExecutionDAO dao = mock(ExecutionDAO.class);
            when(dao.createTasks(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            when(dao.updateWorkflow(any(WorkflowModel.class))).thenAnswer(invocation -> {
                WorkflowModel workflow = invocation.getArgument(0);
                return workflow.getWorkflowId();
            });
            return dao;
        }

        @Bean(name = AgentCoreConfiguration.TASK_MAPPERS_BY_TASK_TYPE)
        Map<String, TaskMapper> taskMappers(ParametersUtils parametersUtils) {
            return Map.of(TaskType.TASK_TYPE_DO_WHILE, new DoWhileTaskMapper(parametersUtils),
                    TaskType.TASK_TYPE_NOOP, new NoopTaskMapper());
        }
    }
}
