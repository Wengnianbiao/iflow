package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.run.FileAccess;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.model.WorkflowExecutionMapper;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransientFileContextTest {

    @Test
    void fileAccessPropagatesInternallyButIsNotSerializedOrPersisted() throws Exception {
        FileAccess access = ignored -> new ByteArrayInputStream(new byte[] {1, 2, 3});
        StartWorkflowRequest request = StartWorkflowRequest.builder()
                .route("upload")
                .input(Map.of("files", Map.of("file", "metadata")))
                .fileAccess(access)
                .build();

        StartWorkflowInput input = StartWorkflowInput.from(request);
        assertSame(access, input.getFileAccess());
        assertFalse(new ObjectMapper().writeValueAsString(request).contains("fileAccess"));

        WorkflowModel model = new WorkflowModel();
        model.setInput(request.getInput());
        model.setFileAccess(access);
        var persisted = WorkflowExecutionMapper.toWorkflowHeader(model);
        WorkflowModel restored = WorkflowExecutionMapper.toModel(persisted);
        assertNull(restored.getFileAccess());
        assertEquals(request.getInput(), restored.getInput());
        assertFalse(new ObjectMapper().writeValueAsString(model).contains("fileAccess"));
    }
}
