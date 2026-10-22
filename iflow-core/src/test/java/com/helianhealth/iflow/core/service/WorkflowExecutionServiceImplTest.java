package com.helianhealth.iflow.core.service;

import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.execution.StartWorkflowInput;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowExecutionServiceImplTest {

    @Test
    void ordinaryEntryDelegatesToNonWaitingEngineEntry() {
        WorkflowExecutor executor = mock(WorkflowExecutor.class);
        when(executor.startWorkflow(any())).thenReturn("workflow-id");
        WorkflowExecutionService service = new WorkflowExecutionServiceImpl(executor);

        assertEquals("workflow-id", service.startWorkflow(request()));

        ArgumentCaptor<StartWorkflowInput> input = ArgumentCaptor.forClass(StartWorkflowInput.class);
        verify(executor).startWorkflow(input.capture());
        assertInput(input.getValue());
        verifyNoMoreInteractions(executor);
    }

    @Test
    void synchronousEntryDelegatesToWaitingEngineEntry() {
        WorkflowExecutor executor = mock(WorkflowExecutor.class);
        WorkflowModel completed = new WorkflowModel();
        completed.setStatus(WorkflowModel.Status.COMPLETED);
        when(executor.startWorkflowSync(any())).thenReturn(completed);
        WorkflowExecutionService service = new WorkflowExecutionServiceImpl(executor);

        assertSame(completed, service.startWorkflowSync(request()));

        ArgumentCaptor<StartWorkflowInput> input = ArgumentCaptor.forClass(StartWorkflowInput.class);
        verify(executor).startWorkflowSync(input.capture());
        assertInput(input.getValue());
        verifyNoMoreInteractions(executor);
    }

    private static StartWorkflowRequest request() {
        return StartWorkflowRequest.builder().route("pacs/apply").version(2).input(Map.of("id", 7)).build();
    }

    private static void assertInput(StartWorkflowInput input) {
        assertEquals("pacs/apply", input.getRoute());
        assertEquals(2, input.getVersion());
        assertEquals(Map.of("id", 7), input.getWorkflowInput());
    }
}
