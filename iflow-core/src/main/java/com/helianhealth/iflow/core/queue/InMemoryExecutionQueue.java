package com.helianhealth.iflow.core.queue;

import com.helianhealth.iflow.core.dao.QueueDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 异步系统任务的内存队列消费者。工作流决策始终由当前触发线程执行。 */
public class InMemoryExecutionQueue implements ExecutionQueue, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryExecutionQueue.class);

    private final QueueDAO queueDAO;
    private final Executor taskExecutor;
    private final Object admissionLock = new Object();
    private final ScheduledThreadPoolExecutor delayedConsumers;
    private boolean closed;

    public InMemoryExecutionQueue(QueueDAO queueDAO, Executor taskExecutor) {
        this.queueDAO = Objects.requireNonNull(queueDAO);
        this.taskExecutor = Objects.requireNonNull(taskExecutor);
        this.delayedConsumers = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "iflow-task-delay");
            thread.setDaemon(true);
            return thread;
        });
        delayedConsumers.setRemoveOnCancelPolicy(true);
    }

    /**
     * 每条新消息唤醒一次消费者；executeTask 必须能处理队列内的任意任务 ID。
     * 工作线程从 QueueDAO 取 ID，不捕获工作流或任务快照。
     * 消息在 ack 前保持唯一，覆盖排队和执行阶段。准入与 pop 互斥，防止拒绝的消息
     * 抢占已接纳消息的消费者。
     *
     * @return false 表示该任务已经排队或正在执行
     */
    @Override
    public boolean submitTask(String queueName, String taskId, long delaySeconds, Consumer<String> executeTask) {
        Objects.requireNonNull(executeTask);
        if (delaySeconds < 0) {
            throw new IllegalArgumentException("delaySeconds must not be negative");
        }
        synchronized (admissionLock) {
            if (closed) {
                throw new RejectedExecutionException("execution queue is closed");
            }
            if (!queueDAO.pushIfNotExists(queueName, taskId, delaySeconds)) {
                return false;
            }
            try {
                if (delaySeconds == 0) {
                    taskExecutor.execute(() -> consume(queueName, executeTask));
                } else {
                    delayedConsumers.schedule(() -> dispatchDelayed(queueName, executeTask),
                            delaySeconds, TimeUnit.SECONDS);
                }
            } catch (RuntimeException | Error error) {
                queueDAO.remove(queueName, taskId);
                throw error;
            }
            return true;
        }
    }

    private void dispatchDelayed(String queueName, Consumer<String> executeTask) {
        synchronized (admissionLock) {
            if (closed) {
                return;
            }
            try {
                taskExecutor.execute(() -> consume(queueName, executeTask));
            } catch (RejectedExecutionException error) {
                // 已接纳的延迟消息保留在队列中，任务池繁忙时稍后重新唤醒消费者。
                LOGGER.debug("Task executor busy for delayed queue {}, retrying dispatch", queueName, error);
                delayedConsumers.schedule(() -> dispatchDelayed(queueName, executeTask),
                        100, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public void close() {
        synchronized (admissionLock) {
            closed = true;
            delayedConsumers.shutdownNow();
        }
    }

    private void consume(String queueName, Consumer<String> executeTask) {
        Throwable failure = null;
        while (true) {
            List<String> taskIds;
            synchronized (admissionLock) {
                taskIds = queueDAO.pop(queueName, 1, 0);
            }
            if (taskIds.isEmpty()) {
                break;
            }
            String taskId = taskIds.get(0);
            try {
                executeTask.accept(taskId);
            } catch (RuntimeException | Error error) {
                if (failure == null) {
                    failure = error;
                } else if (failure != error) {
                    failure.addSuppressed(error);
                }
            } finally {
                queueDAO.ack(queueName, taskId);
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException error) {
            throw error;
        }
    }
}
