package com.helianhealth.agent.core.execution.mapper;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebService 任务 Mapper：对扁平 {@code inputParameters} 做 {@code ${...}} 替换（与 {@link HttpTaskMapper} 相同）。
 *
 * <p>定义层示例（与 HTTP 同构，仅 {@code body} → {@code soapTemplate}）：
 * <pre>{@code
 * "inputParameters": {
 *   "uri": "${workflow.input.uri}",
 *   "headers": { "SOAPAction": "GetUser" },
 *   "templateParameters": { "payload": "${workflow.input}" },
 *   "soapTemplate": "<soap:Envelope>...<id>${$.payload.id}</id>...</soap:Envelope>",
 *   "contentType": "text/xml;charset=UTF-8",
 *   "connectionTimeOut": 5000,
 *   "readTimeOut": 30000
 * }
 * }</pre>
 */
@Component
public class WebServiceTaskMapper implements TaskMapper {

    private final ParametersUtils parametersUtils;

    public WebServiceTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_WEBSERVICE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        WorkflowTask workflowTask = context.getWorkflowTask();
        Map<String, Object> inputParameters =
                workflowTask.getInputParameters() == null
                        ? new HashMap<>()
                        : new HashMap<>(workflowTask.getInputParameters());
        Object soapTemplate = inputParameters.remove("soapTemplate");
        Object rawTemplateParameters = inputParameters.remove("templateParameters");

        Map<String, Object> templateParameters = Map.of();
        if (rawTemplateParameters != null) {
            if (!(rawTemplateParameters instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("templateParameters must be an object");
            }
            templateParameters =
                    parametersUtils.getTaskInput(
                            (Map<String, Object>) rawTemplateParameters,
                            context.getWorkflowModel(),
                            context.getTaskId());
        }

        Map<String, Object> input =
                parametersUtils.getTaskInput(
                        inputParameters, context.getWorkflowModel(), context.getTaskId());
        if (soapTemplate != null) {
            Map<String, Object> resolvedTemplate =
                    parametersUtils.getTaskInput(
                            Map.of("soapTemplate", soapTemplate),
                            context.getWorkflowModel(),
                            context.getTaskId(),
                            templateParameters);
            input.put("soapTemplate", resolvedTemplate.get("soapTemplate"));
        }

        TaskModel webServiceTask = context.createTaskModel();
        webServiceTask.setInputData(input);
        webServiceTask.setStatus(TaskModel.Status.SCHEDULED);
        webServiceTask.setRetryCount(context.getRetryCount());
        return List.of(webServiceTask);
    }
}
