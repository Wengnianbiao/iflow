package com.helianhealth.agent.console.model.response;

import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowExecutionSummaryTest {

    @Test
    void includesDisplayNameFromWorkflowDefinitionSnapshot() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        workflow.setWorkflowDefinition(
                WorkflowDef.builder()
                        .name("create_archive")
                        .displayName("创建档案")
                        .version(2)
                        .build());

        WorkflowExecutionSummary summary = WorkflowExecutionSummary.from(workflow);

        assertEquals("create_archive", summary.getWorkflowName());
        assertEquals("创建档案", summary.getDisplayName());
    }
}
