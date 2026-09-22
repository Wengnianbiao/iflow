package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.TaskUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 汇聚任务。
 *
 * <p>{@code joinOn} 列表声明本 JOIN 等待哪些 fork 分支的引用名；只有当
 * 全部目标任务进入终态才推进自己；任一非可选 / 非宽容失败则立即失败。
 *
 * <p>JOIN 是同步决策屏障：未汇聚时立即返回；Scheduler 完成本轮分支批次后，
 * 由工作流调用线程在下一轮 Decider 中再次评估。
 */
@Component(TaskType.TASK_TYPE_JOIN)
public class Join extends WorkflowSystemTask {
    public Join() {
        super(TaskType.TASK_TYPE_JOIN);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        List<String> joinOn = (List<String>) task.getInputData().get("joinOn");
        if (joinOn == null || joinOn.isEmpty()) {
            task.setStatus(TaskModel.Status.COMPLETED);
            return;
        }
        if (task.isLoopOverTask()) {
            joinOn =
                    joinOn.stream()
                            .map(ref -> TaskUtils.appendIteration(ref, task.getIteration()))
                            .toList();
        }

        List<String> missingRefs =
                joinOn.stream().filter(ref -> workflow.getTaskByRefName(ref) == null).toList();
        if (!missingRefs.isEmpty()) {
            String joinRef = task.getReferenceTaskName();
            String reason =
                    (joinRef == null || joinRef.isBlank())
                            ? "JOIN 配置错误：等待任务 " + String.join(", ", missingRefs) + " 不存在"
                            : "JOIN "
                                    + joinRef
                                    + " 配置错误：等待任务 "
                                    + String.join(", ", missingRefs)
                                    + " 不存在";
            task.setReasonForInCompletion(reason);
            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            return;
        }

        boolean allTerminal = joinOn.stream()
                .map(workflow::getTaskByRefName)
                .allMatch(t -> t != null && t.getStatus().isTerminal());

        StringBuilder optionalFailures = new StringBuilder();
        for (String ref : joinOn) {
            TaskModel forked = workflow.getTaskByRefName(ref);
            if (forked == null) {
                continue;
            }

            task.addOutput(ref, forked.getOutputData());

            TaskModel.Status status = forked.getStatus();
            WorkflowTask def = forked.getWorkflowTask();
            boolean optional = def != null && def.isOptional();
            boolean permissive = def != null && def.isPermissive();
            boolean nonSuccessful = !status.isSuccessful();
            boolean isFailureBlock = nonSuccessful
                    && !optional
                    && (!permissive || allTerminal);

            if (isFailureBlock) {
                String reason = joinOn.stream()
                        .map(workflow::getTaskByRefName)
                        .filter(Objects::nonNull)
                        .filter(t -> !t.getStatus().isSuccessful())
                        .map(TaskModel::getReasonForInCompletion)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(" "));
                task.setReasonForInCompletion(reason);
                task.setStatus(TaskModel.Status.FAILED);
                return;
            }

            if (optional
                    && (status == TaskModel.Status.COMPLETED_WITH_ERRORS
                            || (status.isTerminal() && nonSuccessful))) {
                optionalFailures
                        .append(forked.getTaskDefName())
                        .append('/')
                        .append(forked.getTaskId())
                        .append(' ');
            }
        }

        if (allTerminal) {
            if (!optionalFailures.isEmpty()) {
                optionalFailures.append("completed with errors");
                task.setReasonForInCompletion(optionalFailures.toString());
                task.setStatus(TaskModel.Status.COMPLETED_WITH_ERRORS);
            } else {
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        }
    }
}
