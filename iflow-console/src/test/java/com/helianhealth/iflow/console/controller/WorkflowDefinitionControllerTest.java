package com.helianhealth.iflow.console.controller;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.exception.WorkFlowConfigException;
import com.helianhealth.iflow.common.exception.WorkFlowErrorCode;
import com.helianhealth.iflow.core.service.MetadataService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WorkflowDefinitionControllerTest {
    private final MetadataService metadata = mock(MetadataService.class);
    private final WorkflowDefinitionController controller = new WorkflowDefinitionController(metadata);

    @Test
    void updatesNameWithoutChangingRouteAndVersion() {
        WorkflowDef definition = WorkflowDef.builder().route("GetItemResult").version(2).name("新的检验结果名称").build();
        when(metadata.getWorkflowDef("GetItemResult", 2)).thenReturn(definition);

        assertEquals(definition, controller.update("GetItemResult", 2, definition).getData());
        verify(metadata).updateWorkflowDef(definition);
        verify(metadata, never()).registerWorkflowDef(any());
    }

    @Test
    void rejectsRouteOrVersionChangesBeforeAccessingStorage() {
        WorkflowDef definition = WorkflowDef.builder().route("other").version(2).name("检验结果").build();
        assertThrows(IllegalArgumentException.class, () -> controller.update("GetItemResult", 2, definition));
        assertThrows(IllegalArgumentException.class, () -> controller.update("other", 1, definition));
        verifyNoInteractions(metadata);
    }

    @Test
    void updatingMissingDefinitionDoesNotCreateIt() {
        WorkflowDef definition = WorkflowDef.builder().route("missing").name("检验结果").build();
        when(metadata.getWorkflowDef("missing", 1)).thenThrow(new WorkFlowConfigException(
                WorkFlowErrorCode.WORKFLOW_NOT_FOUND, "工作流定义不存在"));
        assertThrows(WorkFlowConfigException.class, () -> controller.update("missing", 1, definition));
        verify(metadata, never()).updateWorkflowDef(any());
        verify(metadata, never()).registerWorkflowDef(any());
    }
}
