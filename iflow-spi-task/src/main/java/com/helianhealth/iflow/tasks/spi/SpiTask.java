package com.helianhealth.iflow.tasks.spi;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SPI 远程调用系统任务。
 *
 * <p>{@code inputParameters.remoteExtension} 为空 → 默认扩展（返回空 Map）；
 * 非空 → 路由到对应 {@link InterfaceRemoteInvoker} 实现。若节点携带 className/sourceCode，
 * 则在任务开始执行时先注入 ExtensionLoader，再传入 spiInput。扩展返回值直接作为任务根输出。
 */
@Component(TaskType.TASK_TYPE_SPI)
@Slf4j
public class SpiTask extends WorkflowSystemTask {

    private final SpiExtensionRouter extensionRouter;

    public SpiTask(SpiExtensionRouter extensionRouter) {
        super(TaskType.TASK_TYPE_SPI);
        this.extensionRouter = extensionRouter;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        try {
            Map<String, Object> inputData = task.getInputData();
            String remoteExtension = SpiTaskInput.extractRemoteExtension(inputData);
            SpiTaskInput.ExtensionDefinition definition =
                    SpiTaskInput.extractExtensionDefinition(inputData);
            Map<String, Object> spiInput = SpiTaskInput.extractSpiInput(inputData);

            Object result =
                    extensionRouter.invoke(remoteExtension, definition, spiInput);
            task.setOutputData(result);
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error("SPI task failed: workflow={}, task={}", workflow.getWorkflowId(), task.getTaskId(), e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForInCompletion(msg);
            addError(task, msg);
        }
    }
}
