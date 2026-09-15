package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.exception.TerminateWorkflowException;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.execution.evaluators.Evaluator;
import com.helianhealth.agent.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.helianhealth.agent.common.domain.metadata.task.TaskType.TASK_TYPE_INLINE;

/**
 * 内联脚本任务。
 *
 * <p>按 inputParameters 中的 {@code evaluatorType}（默认 {@code graaljs}）选用对应
 * {@link Evaluator} 求值 {@code expression}，返回值直接作为 outputData。
 *
 * <p>支持的 evaluatorType：
 * <ul>
 *   <li>{@code graaljs} — GraalJS 脚本，{@code $} 为 input</li>
 *   <li>{@code spel} — SpEL，{@code #root} 为 input</li>
 *   <li>{@code value-param} — 按 expression 作为键从 input 取值</li>
 * </ul>
 *
 * <p>声明式参数映射请使用 {@link TaskType#TASK_TYPE_MAPPING}。
 */
@Component(TASK_TYPE_INLINE)
public class Inline extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(Inline.class);

    public static final String INPUT_EVALUATOR_TYPE = "evaluatorType";
    public static final String INPUT_EXPRESSION = "expression";
    public static final String DEFAULT_EVALUATOR = GraalJSEvaluator.NAME;

    private final Map<String, Evaluator> evaluators;

    public Inline(Map<String, Evaluator> evaluators) {
        super(TASK_TYPE_INLINE);
        this.evaluators = evaluators;
    }

    @Override
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> input = task.getInputData();
        String evaluatorType = (String) input.getOrDefault(INPUT_EVALUATOR_TYPE, DEFAULT_EVALUATOR);
        String expression = (String) input.get(INPUT_EXPRESSION);

        try {
            checkEvaluatorType(evaluatorType);
            checkExpression(expression);
            Evaluator evaluator = evaluators.get(evaluatorType);
            Object result = evaluator.evaluate(expression, input);
            task.setOutputData(result);
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            LOGGER.error("INLINE task evaluation failed: workflow={}, task={}",
                    workflow.getWorkflowId(), task.getTaskId(), e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setOutputData(null);
            task.setReasonForInCompletion(msg);
            addError(task, msg);
        }
    }

    private void checkEvaluatorType(String evaluatorType) {
        if (StringUtils.isBlank(evaluatorType)) {
            LOGGER.error("Empty {} in INLINE task. ", INPUT_EVALUATOR_TYPE);
            throw new TerminateWorkflowException(
                    "Empty '"
                            + INPUT_EVALUATOR_TYPE
                            + "' in INLINE task's input parameters. A non-empty String value must be provided.");
        }
        if (evaluators.get(evaluatorType) == null) {
            LOGGER.error("Evaluator {} for INLINE task not registered", evaluatorType);
            throw new TerminateWorkflowException(
                    "Unknown evaluator '" + evaluatorType + "' in INLINE task.");
        }
    }

    private void checkExpression(String expression) {
        if (StringUtils.isBlank(expression)) {
            LOGGER.error("Empty {} in INLINE task. ", INPUT_EXPRESSION);
            throw new TerminateWorkflowException(
                    "Empty '"
                            + INPUT_EXPRESSION
                            + "' in Inline task's input parameters. A non-empty String value must be provided.");
        }
    }
}
