package com.helianhealth.iflow.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dao.MetadataDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DoWhileDefinitionValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {"register", "update", "save"})
    void definitionWritesPreserveExclusiveArrayAndJsConfiguration(String operation) {
        MetadataDAO dao = mock(MetadataDAO.class);
        WorkflowTask collection = arrayLoop();
        WorkflowTask condition = jsLoop();
        WorkflowDef definition = WorkflowDef.builder().route("valid-loops").tasks(List.of(collection, condition)).build();
        when(dao.getWorkflowDef("valid-loops", 1)).thenReturn(Optional.empty());
        write(new MetadataServiceImpl(dao), definition, operation);
        assertEquals("${workflow.input.items}", collection.getLoopItems());
        assertNull(collection.getLoopCondition());
        assertEquals("$.iteration < 3", condition.getLoopCondition());
        assertNull(condition.getLoopItems());
        if ("update".equals(operation)) verify(dao).updateWorkflowDef(definition);
        else verify(dao).createWorkflowDef(definition);
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "update", "save"})
    void invalidNestedLoopIsRejectedBeforeAccessingDao(String operation) {
        WorkflowTask invalid = arrayLoop(); invalid.setLoopCondition("false");
        WorkflowTask outer = jsLoop(); outer.setLoopOver(List.of(invalid));
        WorkflowTask choice = WorkflowTask.builder().type("SWITCH").decisionCases(Map.of("chosen", List.of(outer))).build();
        WorkflowTask fork = WorkflowTask.builder().type("FORK_JOIN").forkTasks(List.of(List.of(choice))).build();
        MetadataDAO dao = mock(MetadataDAO.class);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> write(new MetadataServiceImpl(dao), WorkflowDef.builder().tasks(List.of(fork)).build(), operation));
        assertTrue(error.getMessage().contains("exactly one"));
        verifyNoInteractions(dao);
    }

    @ParameterizedTest
    @MethodSource("invalidDefinitions")
    void rejectsMissingBlankLiteralReservedAndUnsupportedConfiguration(WorkflowTask loop) {
        MetadataDAO dao = mock(MetadataDAO.class);
        WorkflowTask choice = WorkflowTask.builder().type("SWITCH").defaultCase(List.of(loop)).build();
        assertThrows(IllegalArgumentException.class,
                () -> new MetadataServiceImpl(dao).registerWorkflowDef(WorkflowDef.builder().tasks(List.of(choice)).build()));
        verifyNoInteractions(dao);
    }

    static Stream<WorkflowTask> invalidDefinitions() {
        WorkflowTask missing = jsLoop(); missing.setLoopCondition(null);
        WorkflowTask blankJs = jsLoop(); blankJs.setLoopCondition(" ");
        WorkflowTask blankItems = arrayLoop(); blankItems.setLoopItems("");
        WorkflowTask literal = arrayLoop(); literal.setLoopItems("[1,2]");
        WorkflowTask reserved = arrayLoop(); reserved.setInputParameters(Map.of("loopItems", List.of()));
        WorkflowTask unsupported = jsLoop(); unsupported.setEvaluatorType("value-param");
        WorkflowTask empty = jsLoop(); empty.setLoopOver(List.of());
        return Stream.of(missing, blankJs, blankItems, literal, reserved, unsupported, empty);
    }

    @Test
    void loopItemsAndJsConditionRoundTripWithOnlyNewWireField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowTask array = arrayLoop();
        array.setInputParameters(Map.of("values", List.of(Map.of("id", 1))));
        WorkflowDef original = WorkflowDef.builder().route("serialized-loops").tasks(List.of(array, jsLoop())).build();
        String json = mapper.writeValueAsString(original);
        WorkflowDef restored = mapper.readValue(json, WorkflowDef.class);
        assertEquals("${workflow.input.items}", restored.getTasks().get(0).getLoopItems());
        assertNull(restored.getTasks().get(0).getLoopCondition());
        assertNull(restored.getTasks().get(1).getLoopItems());
        assertEquals("$.iteration < 3", restored.getTasks().get(1).getLoopCondition());
        assertEquals(List.of(Map.of("id", 1)), restored.getTasks().get(0).getInputParameters().get("values"));
        assertTrue(mapper.readTree(json).get("tasks").get(0).has("loopItems"));
        assertFalse(mapper.readTree(json).get("tasks").get(0).has("items"));
    }

    private static void write(MetadataService service, WorkflowDef definition, String operation) {
        switch (operation) {
            case "register" -> service.registerWorkflowDef(definition);
            case "update" -> service.updateWorkflowDef(definition);
            case "save" -> assertSame(definition, service.saveWorkflowDef(definition));
            default -> fail("unexpected write operation");
        }
    }

    private static WorkflowTask jsLoop() {
        return WorkflowTask.builder().type("DO_WHILE").taskReferenceName("loop").loopCondition("$.iteration < 3")
                .loopOver(List.of(WorkflowTask.builder().type("INLINE").taskReferenceName("body").build())).build();
    }

    private static WorkflowTask arrayLoop() {
        WorkflowTask loop = jsLoop(); loop.setLoopCondition(null); loop.setLoopItems("${workflow.input.items}"); return loop;
    }
}
