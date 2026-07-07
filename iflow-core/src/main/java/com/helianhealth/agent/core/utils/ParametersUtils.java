package com.helianhealth.agent.core.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 解析工作流 / 任务定义中的 {@code ${...}} 占位符
 *
 * <p>由 TaskMapper 在 Decider 调度阶段调用，Task 运行时只消费已替换完毕的 {@link TaskModel#getInputData()}。
 */
@Component
public class ParametersUtils {

    private static final Pattern PATTERN =
            Pattern.compile(
                    "(?=(?<!\\$)\\$\\{)(?:(?=.*?\\{(?!.*?\\1)(.*\\}(?!.*\\2).*))(?=.*?\\}(?!.*?\\2)(.*)).)+?.*?(?=\\1)[^{]*(?=\\2$)",
                    Pattern.DOTALL);

    private final TypeReference<Map<String, Object>> mapType = new TypeReference<>() {};

    public Map<String, Object> getTaskInput(
            Map<String, Object> input, WorkflowModel workflow, String taskId) {
        return getTaskInput(input, workflow, taskId, Map.of());
    }

    /**
     * 解析任务入参，并把额外值合并到 JsonPath 根上下文。
     *
     * <p>额外值用于 WebService 等需要二阶段模板绑定的任务，例如局部参数
     * {@code payload} 可通过 {@code ${$.payload.id}} 引用。额外值不能覆盖
     * {@code workflow} 或已存在的任务引用名。
     */
    public Map<String, Object> getTaskInput(
            Map<String, Object> input,
            WorkflowModel workflow,
            String taskId,
            Map<String, Object> additionalContext) {
        Map<String, Object> inputParams = input == null ? new HashMap<>() : clone(input);

        Map<String, Object> inputMap = new HashMap<>();
        Map<String, Object> workflowParams = new HashMap<>();
        workflowParams.put("input", workflow.getInput());
        workflowParams.put("output", workflow.getOutput());
        workflowParams.put("status", workflow.getStatus());
        workflowParams.put("workflowId", workflow.getWorkflowId());
        workflowParams.put("workflowType", workflow.getWorkflowName());
        workflowParams.put("version", workflow.getWorkflowVersion());
        workflowParams.put("correlationId", workflow.getCorrelationId());
        workflowParams.put("reasonForInCompletion", workflow.getReasonForInCompletion());
        inputMap.put("workflow", workflowParams);

        workflow.getTasks()
                .stream()
                .map(TaskModel::getReferenceTaskName)
                .filter(Objects::nonNull)
                .map(workflow::getTaskByRefName)
                .filter(Objects::nonNull)
                .forEach(task -> {
                    Map<String, Object> taskParams = new HashMap<>();
                    taskParams.put("input", task.getInputData());
                    taskParams.put("output", task.getOutputData());
                    taskParams.put("taskType", task.getTaskType());
                    if (task.getStatus() != null) {
                        taskParams.put("status", task.getStatus().name());
                    }
                    taskParams.put("referenceTaskName", task.getReferenceTaskName());
                    taskParams.put("retryCount", task.getRetryCount());
                    taskParams.put("correlationId", task.getCorrelationId());
                    taskParams.put("taskDefName", task.getTaskDefName());
                    taskParams.put("scheduledTime", task.getScheduledTime());
                    taskParams.put("startTime", task.getStartTime());
                    taskParams.put("endTime", task.getEndTime());
                    taskParams.put("workflowInstanceId", task.getWorkflowInstanceId());
                    taskParams.put("taskId", task.getTaskId());
                    taskParams.put("reasonForInCompletion", task.getReasonForInCompletion());
                    taskParams.put("iteration", task.getIteration());
                    inputMap.put(taskRefKey(task), taskParams);
                });

        if (additionalContext != null) {
            additionalContext.forEach(
                    (key, value) -> {
                        if (key == null || key.isBlank()) {
                            throw new IllegalArgumentException(
                                    "template parameter name must not be blank");
                        }
                        if (inputMap.containsKey(key)) {
                            throw new IllegalArgumentException(
                                    "template parameter name conflicts with reserved context key: "
                                            + key);
                        }
                        inputMap.put(key, value);
                    });
        }

        Configuration option =
                Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
        DocumentContext documentContext = JsonPath.parse(inputMap, option);
        return replace(inputParams, documentContext, taskId);
    }

    public Map<String, Object> replace(Map<String, Object> input, Object json) {
        Configuration option =
                Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
        DocumentContext documentContext =
                JsonPath.parse(json instanceof String ? json.toString() : json, option);
        return replace(input, documentContext, null);
    }

    private Map<String, Object> clone(Map<String, Object> input) {
        return JSON.parseObject(JSON.toJSONString(input), mapType);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> replace(
            Map<String, Object> input, DocumentContext documentContext, String taskId) {
        Map<String, Object> result = new HashMap<>();
        for (Entry<String, Object> entry : input.entrySet()) {
            Object newValue;
            Object value = entry.getValue();
            if (value instanceof String stringValue) {
                newValue = replaceVariables(stringValue, documentContext, taskId);
            } else if (value instanceof Map<?, ?> mapValue) {
                newValue = replace((Map<String, Object>) mapValue, documentContext, taskId);
            } else if (value instanceof List<?> listValue) {
                newValue = replaceList(listValue, taskId, documentContext);
            } else {
                newValue = value;
            }
            result.put(entry.getKey(), newValue);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object replaceList(List<?> values, String taskId, DocumentContext documentContext) {
        List<Object> replacedList = new LinkedList<>();
        for (Object listVal : values) {
            if (listVal instanceof String stringVal) {
                replacedList.add(replaceVariables(stringVal, documentContext, taskId));
            } else if (listVal instanceof Map<?, ?> mapVal) {
                replacedList.add(replace((Map<String, Object>) mapVal, documentContext, taskId));
            } else if (listVal instanceof List<?> nestedList) {
                replacedList.add(replaceList(nestedList, taskId, documentContext));
            } else {
                replacedList.add(listVal);
            }
        }
        return replacedList;
    }

    private Object replaceVariables(String paramString, DocumentContext documentContext, String taskId) {
        return replaceVariables(paramString, documentContext, taskId, 0);
    }

    private Object replaceVariables(
            String paramString, DocumentContext documentContext, String taskId, int depth) {
        var matcher = PATTERN.matcher(paramString);
        var replacements = new LinkedList<Replacement>();
        while (matcher.find()) {
            var start = matcher.start();
            var end = matcher.end();
            var match = paramString.substring(start, end);
            String paramPath = match.substring(2, match.length() - 1);
            paramPath = replaceVariables(paramPath, documentContext, taskId, depth + 1).toString();
            if (paramPath.isBlank()) {
                replacements.add(new Replacement("", start, end));
                continue;
            }
            try {
                replacements.add(new Replacement(documentContext.read(paramPath), start, end));
            } catch (Exception ignored) {
                replacements.add(new Replacement(null, start, end));
            }
        }
        if (replacements.size() == 1
                && replacements.getFirst().getStartIndex() == 0
                && replacements.getFirst().getEndIndex() == paramString.length()
                && depth == 0) {
            return replacements.get(0).getReplacement();
        }
        var builder = new StringBuilder(paramString);
        replacements.sort(Collections.reverseOrder());
        for (Replacement replacement : replacements) {
            builder.replace(
                    replacement.getStartIndex(),
                    replacement.getEndIndex(),
                    Objects.toString(replacement.getReplacement(), ""));
        }
        return builder.toString().replaceAll("\\$\\$\\{", "\\${");
    }

    private static String taskRefKey(TaskModel task) {
        String refName = task.getReferenceTaskName();
        if (task.isLoopOverTask() && refName != null) {
            int idx = refName.lastIndexOf("__");
            if (idx > 0) {
                return refName.substring(0, idx);
            }
        }
        return refName;
    }

    private static final class Replacement implements Comparable<Replacement> {
        private final int startIndex;
        private final int endIndex;
        private final Object replacement;

        Replacement(Object replacement, int startIndex, int endIndex) {
            this.replacement = replacement;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        Object getReplacement() {
            return replacement;
        }

        int getStartIndex() {
            return startIndex;
        }

        int getEndIndex() {
            return endIndex;
        }

        @Override
        public int compareTo(Replacement other) {
            return Integer.compare(startIndex, other.startIndex);
        }
    }
}
