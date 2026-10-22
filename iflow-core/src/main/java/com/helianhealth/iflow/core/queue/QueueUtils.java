package com.helianhealth.iflow.core.queue;

import com.helianhealth.iflow.core.model.TaskModel;

/** 队列命名。当前没有 domain、隔离组和执行命名空间，队列名就是任务类型。 */
public final class QueueUtils {

    private QueueUtils() {}

    public static String getQueueName(TaskModel task) {
        if (task == null || task.getTaskType() == null || task.getTaskType().isBlank()) {
            throw new IllegalArgumentException("任务类型不能为空，无法确定队列名");
        }
        return task.getTaskType();
    }
}
