package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MarkerTaskTest {

    @ParameterizedTest
    @MethodSource("markerTasks")
    void completesWithoutReplacingMapperDecisionOrChangingWorkflow(
            WorkflowSystemTask systemTask, String type) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setOutput(Map.of("business", "unchanged"));
        TaskModel task = new TaskModel();
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        Map<String, Object> input = Map.of("case", "accepted", "hasChildren", "true");
        Map<String, Object> decision = Map.of("selectedCase", "accepted");
        task.setInputData(input);
        task.setOutputData(decision);

        systemTask.execute(workflow, task, null);

        assertEquals(type, systemTask.getTaskType());
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(input, task.getInputData());
        assertEquals(decision, task.getOutputData());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertEquals(Map.of("business", "unchanged"), workflow.getOutput());
        assertTrue(workflow.getTasks().isEmpty());
    }

    private static Stream<Arguments> markerTasks() {
        return Stream.of(Arguments.of(new Noop(), "NOOP"),
                Arguments.of(new Fork(), "FORK"), Arguments.of(new Switch(), "SWITCH"));
    }
}
