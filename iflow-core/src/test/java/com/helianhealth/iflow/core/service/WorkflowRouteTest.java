package com.helianhealth.iflow.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.dao.MetadataDAO;
import com.helianhealth.iflow.core.execution.StartWorkflowInput;
import com.helianhealth.iflow.core.model.WorkflowExecutionMapper;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WorkflowRouteTest {
    @Test
    void resolvesExplicitAndLatestVersionsByRouteEvenWithDuplicateNames() {
        MetadataDAO dao = mock(MetadataDAO.class);
        var service = new MetadataServiceImpl(dao);
        var first = WorkflowDef.builder().route("first").version(1).name("同一个中文名称").build();
        var latest = WorkflowDef.builder().route("first").version(2).name("修改后的名称").build();
        var other = WorkflowDef.builder().route("other").version(1).name("同一个中文名称").build();
        when(dao.getWorkflowDef("first", 1)).thenReturn(Optional.of(first));
        when(dao.getLatestWorkflowDef("first")).thenReturn(Optional.of(latest));
        when(dao.getWorkflowDef("other", 1)).thenReturn(Optional.of(other));
        var input = StartWorkflowInput.from(StartWorkflowRequest.builder().route("first").version(1).build());
        assertEquals(first, service.getWorkflowDef(input.getRoute(), input.getVersion()));
        assertEquals(latest, service.getWorkflowDef("first", null));
        assertEquals(other, service.getWorkflowDef("other", 1));
        verify(dao, never()).getLatestWorkflowDef("同一个中文名称");
    }

    @Test
    void runtimeSnapshotsAndWorkflowRouteExpressionsKeepTheRouteSeparateFromTheName() {
        var model = new WorkflowModel();
        model.setWorkflowDefinition(WorkflowDef.builder().route("GetItemResult").name("获取检验结果").version(3).build());
        model.setWorkflowId("instance-1");
        var restored = WorkflowExecutionMapper.toModel(WorkflowExecutionMapper.toWorkflowHeader(model));
        assertEquals("GetItemResult", restored.getWorkflowRoute());
        assertEquals("获取检验结果", restored.getWorkflowName());
        assertEquals("GetItemResult.3/instance-1", restored.toShortString());
        var parameters = new ParametersUtils(new ObjectMapper()).getTaskInput(Map.of("route", "${workflow.workflowRoute}"), restored, null);
        assertEquals("GetItemResult", parameters.get("route"));
    }
}
