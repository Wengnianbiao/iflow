package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费框架已解析的任务输入，保留模板参数类型和任务阶段的局部路径。
 *
 * <p>定义层示例：
 * <pre>{@code
 * "inputParameters": {
 *   "uri": "${workflow.input.uri}",
 *   "headers": { "SOAPAction": "GetUser" },
 *   "templateParameters": { "payload": "${workflow.input}" },
 *   "soapTemplate": "<request><id>$.payload.id</id></request>",
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
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        Map<String, Object> input =
                context.getTaskInput() != null
                        ? new HashMap<>(context.getTaskInput())
                        : parametersUtils.getTaskInput(
                                context.getWorkflowTask().getInputParameters(),
                                context.getWorkflowModel(), context.getTaskId());

        TaskModel webServiceTask = context.createTaskModel();
        webServiceTask.setInputData(input);
        webServiceTask.setStatus(TaskModel.Status.SCHEDULED);
        webServiceTask.setRetryCount(context.getRetryCount());
        return List.of(webServiceTask);
    }
}
