package com.helianhealth.iflow.core.queue;

import java.util.function.Consumer;

/** 异步任务的队列准入与消费接口；实现负责去重和分发，不参与工作流决策。 */
public interface ExecutionQueue {

    /**
     * 接纳任务并安排异步消费，重复的任务不再次提交。
     * executeTask 是按 ID 加载并执行任务的回调，必须能处理指定队列内的任意任务 ID。
     * 实现不得在调用线程等待任务完成；拒绝提交应抛出运行时异常并回滚本次准入。
     *
     * @return true 表示接纳新任务，false 表示该任务已排队或正在执行
     */
    boolean submitTask(String queueName, String taskId, Consumer<String> executeTask);
}
