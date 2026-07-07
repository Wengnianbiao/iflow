package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.execution.evaluators.Evaluator;
import com.helianhealth.agent.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 内联脚本任务。
 *
 * <p>按 inputParameters 中的 {@code evaluatorType}（默认 {@code graaljs}）选用对应
 * {@link Evaluator} 求值 {@code expression}，结果写入 outputData.result。
 *
 * <p>支持的 evaluatorType：
 * <ul>
 *   <li>{@code graaljs} / {@code javascript} — GraalJS 脚本，{@code $} 为 input</li>
 *   <li>{@code spel} — SpEL，{@code #root} 为 input</li>
 *   <li>{@code value-param} — 按 expression 作为键从 input 取值</li>
 * </ul>
 *
 * <p>声明式参数映射请使用 {@link TaskType#TASK_TYPE_PARAM_MAP}。
 */
@Component(TaskType.TASK_TYPE_INLINE)
@Slf4j
public class Inline extends WorkflowSystemTask {

    public static final String INPUT_EVALUATOR_TYPE = "evaluatorType";
    public static final String INPUT_EXPRESSION = "expression";
    public static final String OUTPUT_RESULT = "result";
    public static final String DEFAULT_EVALUATOR = GraalJSEvaluator.NAME;

    private final Map<String, Evaluator> evaluators;

    public Inline(Map<String, Evaluator> evaluators) {
        super(TaskType.TASK_TYPE_INLINE);
        this.evaluators = evaluators;
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> input = task.getInputData();
        String evaluatorType = (String) input.getOrDefault(INPUT_EVALUATOR_TYPE, DEFAULT_EVALUATOR);
        String expression = (String) input.get(INPUT_EXPRESSION);

        if (expression == null || expression.isBlank()) {
            task.setReasonForInCompletion("empty expression");
            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            return true;
        }
        Evaluator evaluator = evaluators.get(evaluatorType);
        if (evaluator == null) {
            task.setReasonForInCompletion("unknown evaluatorType: " + evaluatorType);
            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            return true;
        }

        try {
            Object result = evaluator.evaluate(expression, input);
            task.addOutput(OUTPUT_RESULT, result);
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error("INLINE task evaluation failed: workflow={}, task={}",
                    workflow.getWorkflowId(), task.getTaskId(), e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForInCompletion(msg);
            task.addOutput("error", msg);
        }
        return true;
    }
}
