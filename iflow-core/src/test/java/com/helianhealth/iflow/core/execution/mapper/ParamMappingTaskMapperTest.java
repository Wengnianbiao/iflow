package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParamMappingTaskMapperTest {

    private final ParamMappingTaskMapper mapper = new ParamMappingTaskMapper(new ParametersUtils());

    @Test
    void resolvesPayloadExpression() {
        List<Map<String, Object>> configs =
                List.of(Map.of("sourceParamKey", "$.patientCode", "targetParamKey", "patientCode",
                        "targetParamType", "STRING", "mappingType", "NAME"));
        WorkflowTask workflowTask =
                WorkflowTask.builder()
                        .name("map parameters")
                        .taskReferenceName("map_parameters_ref")
                        .type(TaskType.TASK_TYPE_MAPPING)
                        .inputParameters(
                                Map.of(
                                        ParamMappingTaskMapper.PAYLOAD, "${workflow.input.data}",
                                        ParamMappingTaskMapper.PARAMS_CONFIG, configs))
                        .build();
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        workflow.setInput(Map.of("data", Map.of("patientCode", "P001")));
        TaskMapperContext context =
                TaskMapperContext.builder()
                        .workflowModel(workflow)
                        .workflowTask(workflowTask)
                        .taskId("task-1")
                        .build();

        TaskModel task = mapper.getMappedTasks(context).get(0);

        assertEquals(TaskType.TASK_TYPE_MAPPING, mapper.getTaskType());
        assertEquals(Map.of("patientCode", "P001"), task.getInputData().get(ParamMappingTaskMapper.PAYLOAD));
        assertEquals(configs, task.getInputData().get(ParamMappingTaskMapper.PARAMS_CONFIG));
    }

    @Test
    void resolvesPayloadParameterObject() {
        List<Map<String, Object>> configs =
                List.of(Map.of("sourceParamKey", "$.patientCode", "targetParamKey", "patientCode",
                        "targetParamType", "STRING", "mappingType", "NAME"));
        WorkflowTask workflowTask =
                WorkflowTask.builder()
                        .name("map parameters")
                        .taskReferenceName("map_parameters_ref")
                        .type(TaskType.TASK_TYPE_MAPPING)
                        .inputParameters(
                                Map.of(
                                        ParamMappingTaskMapper.PAYLOAD,
                                        Map.of(
                                                "patientCode", "${workflow.input.patientCode}",
                                                "hospitalCode", "H001"),
                                        ParamMappingTaskMapper.PARAMS_CONFIG, configs))
                        .build();
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        workflow.setInput(Map.of("patientCode", "P001"));
        TaskMapperContext context =
                TaskMapperContext.builder()
                        .workflowModel(workflow)
                        .workflowTask(workflowTask)
                        .taskId("task-1")
                        .build();

        TaskModel task = mapper.getMappedTasks(context).get(0);

        assertEquals("P001", ((Map<?, ?>) task.getInputData().get(ParamMappingTaskMapper.PAYLOAD)).get("patientCode"));
        assertEquals("H001", ((Map<?, ?>) task.getInputData().get(ParamMappingTaskMapper.PAYLOAD)).get("hospitalCode"));
        assertEquals(configs, task.getInputData().get(ParamMappingTaskMapper.PARAMS_CONFIG));
    }

    @Test
    void rejectsDefinitionWithoutPayload() {
        WorkflowTask workflowTask =
                WorkflowTask.builder()
                        .name("map parameters")
                        .taskReferenceName("map_parameters_ref")
                        .type(TaskType.TASK_TYPE_MAPPING)
                        .inputParameters(
                                Map.of(
                                        ParamMappingTaskMapper.PARAMS_CONFIG,
                                        List.of(Map.of("targetParamKey", "value"))))
                        .build();
        TaskMapperContext context =
                TaskMapperContext.builder()
                        .workflowModel(new WorkflowModel())
                        .workflowTask(workflowTask)
                        .taskId("task-1")
                        .build();

        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mapper.getMappedTasks(context));

        assertEquals(
                "MAPPING inputParameters must contain payload and paramsConfig",
                error.getMessage());
    }
}
