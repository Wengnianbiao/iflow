package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.config.IflowProperties;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.lock.ExecutionLockService;
import com.helianhealth.iflow.core.lock.InMemoryLock;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowTaskPolicyResolutionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothStartEntriesPreserveNodeDefaultsAndFreezeAnIndependentSnapshot(boolean synchronous) {
        ObjectMapper mapper = new ObjectMapper();
        IflowProperties properties = new IflowProperties();
        WorkflowTask defaulted = node("defaulted");
        WorkflowTask explicit = WorkflowTask.builder().name("explicit").taskReferenceName("explicit")
                .retryCount(2).retryDelaySeconds(3).retryLogic(WorkflowTask.RetryLogic.LINEAR_BACKOFF)
                .startDelay(5).build();
        WorkflowTask partial = node("partial");
        partial.setRetryCount(1);
        partial.setStartDelay(0);
        WorkflowTask loop = WorkflowTask.builder().name("loop").taskReferenceName("loop")
                .type("DO_WHILE").loopOver(List.of(partial)).build();
        WorkflowTask fork = WorkflowTask.builder().name("fork").taskReferenceName("fork")
                .type("FORK_JOIN").forkTasks(List.of(List.of(explicit))).build();
        WorkflowTask branch = WorkflowTask.builder().name("branch").taskReferenceName("branch")
                .type("SWITCH").decisionCases(Map.of("yes", List.of(defaulted, fork)))
                .defaultCase(List.of(loop)).build();
        List<WorkflowTask> tasks = new ArrayList<>(List.of(branch));
        for (String type : List.of("INLINE", "MAPPING", "SPI", "JDBC", "WEBSERVICE",
                "REGISTERED_ASYNC", "REGISTERED_SYNC", "UNREGISTERED",
                "DECISION", "FORK", "JOIN", "EXCLUSIVE_JOIN")) {
            WorkflowTask task = node(type.toLowerCase());
            task.setType(type);
            tasks.add(task);
        }
        WorkflowDef original = WorkflowDef.builder().route("test")
                .tasks(tasks).build();
        WorkflowDef before = mapper.convertValue(original, WorkflowDef.class);

        MetadataService metadata = mock(MetadataService.class);
        when(metadata.getWorkflowDef("test", 2)).thenReturn(original);
        ExecutionDAOFacade dao = mock(ExecutionDAOFacade.class);
        AtomicReference<WorkflowModel> created = new AtomicReference<>();
        when(dao.createWorkflow(any())).thenAnswer(invocation -> {
            WorkflowModel workflow = invocation.getArgument(0);
            created.set(workflow);
            return workflow.getWorkflowId();
        });
        DeciderService decider = mock(DeciderService.class);
        DeciderService.DeciderOutcome completed = mock(DeciderService.DeciderOutcome.class);
        completed.isComplete = true;
        completed.tasksToBeUpdated = List.of();
        when(decider.decide(any())).thenReturn(completed);
        WorkflowCompletionRegistry completions = new WorkflowCompletionRegistry();
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(
                asyncTask("HTTP"), asyncTask("JDBC"), asyncTask("WEBSERVICE"), asyncTask("SPI"),
                asyncTask("REGISTERED_ASYNC"), new WorkflowSystemTask("REGISTERED_SYNC") {}));
        WorkflowExecutorOps executor = new WorkflowExecutorOps(decider, registry,
                dao, metadata, new IDGenerator(), new ParametersUtils(mapper),
                new ExecutionLockService(new InMemoryLock()), null, completions, List.of(completions), properties, mapper);
        StartWorkflowInput input = new StartWorkflowInput();
        input.setRoute("test");
        input.setVersion(2);
        if (synchronous) assertEquals(WorkflowModel.Status.COMPLETED, executor.startWorkflowSync(input).getStatus());
        else assertNotNull(executor.startWorkflow(input));

        WorkflowDef snapshot = created.get().getWorkflowDefinition();
        assertNotSame(original, snapshot);
        assertEquals(before, original);
        assertEquals(60, snapshot.getTimeoutSeconds());
        WorkflowTask resolved = snapshot.getTaskByRefName("defaulted");
        assertEquals(0, resolved.getRetryCount());
        assertEquals(0, resolved.getRetryDelaySeconds());
        assertEquals(WorkflowTask.RetryLogic.FIXED, resolved.getRetryLogic());
        assertEquals(0, resolved.getStartDelay());
        assertEquals(explicit, snapshot.getTaskByRefName("explicit"));
        WorkflowTask resolvedPartial = snapshot.getTaskByRefName("partial");
        assertEquals(1, resolvedPartial.getRetryCount());
        assertEquals(0, resolvedPartial.getStartDelay());
        assertEquals(0, resolvedPartial.getRetryDelaySeconds());
        for (WorkflowTask task : snapshot.collectTasks()) {
            assertNotNull(task.getStartDelay());
            assertNotNull(task.getRetryCount());
            assertNotNull(task.getRetryDelaySeconds());
            assertNotNull(task.getRetryLogic());
        }

        defaulted.setRetryCount(7);
        defaulted.setRetryDelaySeconds(5);
        defaulted.setStartDelay(10);
        assertEquals(0, resolved.getRetryCount());
        assertEquals(0, resolved.getRetryDelaySeconds());
        assertEquals(0, resolved.getStartDelay());
        verify(metadata).getWorkflowDef("test", 2);
    }

    private static WorkflowTask node(String reference) {
        return WorkflowTask.builder().name(reference).taskReferenceName(reference).type("HTTP").build();
    }

    private static WorkflowSystemTask asyncTask(String type) {
        return new WorkflowSystemTask(type) {
            @Override
            public boolean isAsync() {
                return true;
            }
        };
    }
}
