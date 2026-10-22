package com.helianhealth.iflow.core.dao;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 异步任务调度队列。
 *
 * <p>调度语义参考 Conductor {@code QueueDAO}：按队列名保存任务 ID，支持延迟可见、优先级和未确认租约。
 * 当前框架只提供内存实现 {@code InMemoryQueueDAO}，进程退出后消息不会保留，也不从数据库恢复。
 */
public interface QueueDAO {

    /**
     * 入队。相同消息 ID 会覆盖原消息，并按新的延迟重新计算可见时间。
     *
     * @param offsetTimeInSecond 多少秒后可见；0 表示立即可见
     */
    void push(String queueName, String id, long offsetTimeInSecond);

    /**
     * @param priority 优先级，数值越大越先被取出，约定范围 0 到 99
     * @param offsetTimeInSecond 多少秒后可见
     */
    void push(String queueName, String id, int priority, long offsetTimeInSecond);

    /** 按毫秒精度延迟入队，避免亚秒延迟被截成 0。 */
    default void push(String queueName, String id, int priority, Duration offsetTime) {
        push(queueName, id, priority, offsetTime.getSeconds());
    }

    /**
     * @return 消息不存在并成功入队时返回 true；已存在时返回 false，且不修改原消息
     */
    boolean pushIfNotExists(String queueName, String id, long offsetTimeInSecond);

    /**
     * @return 消息不存在并成功入队时返回 true
     */
    boolean pushIfNotExists(String queueName, String id, int priority, long offsetTimeInSecond);

    /**
     * 取出已到可见时间的消息，并进入未确认状态，直到 ack、remove 或租约到期。
     *
     * @param count 最多取出的条数
     * @param timeout 队列为空时的最长等待，单位毫秒；0 表示不等待
     */
    List<String> pop(String queueName, int count, int timeout);

    /** 无论消息是否已被取出，都从队列删除。 */
    void remove(String queueName, String messageId);

    /** 尚未被取出的消息数量，包含尚未到可见时间的延迟消息。 */
    int getSize(String queueName);

    /**
     * 确认消息并删除。
     *
     * @return 消息存在并已删除时返回 true
     */
    boolean ack(String queueName, String messageId);

    /**
     * 把未确认租约改成从现在起的指定时长。
     *
     * @param unackTimeout 租约，单位毫秒。0 表示立即重新可见
     * @return 消息存在并已更新时返回 true
     */
    boolean setUnackTimeout(String queueName, String messageId, long unackTimeout);

    /**
     * 只有新的可见时间早于当前安排时才更新，避免把已经提前的消息再推迟。
     */
    default boolean setUnackTimeoutIfShorter(
            String queueName, String messageId, long unackTimeout) {
        return setUnackTimeout(queueName, messageId, unackTimeout);
    }

    /** 清空指定队列。 */
    void flush(String queueName);

    /** 队列名到等待消息数。 */
    Map<String, Long> queuesDetail();

    /**
     * 队列名、分片名，再到 size（等待中）和 uacked（已取出未确认）。
     * 内存实现只有一个名为 memory 的分片。
     */
    Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose();

    /** 把已到期的未确认消息放回可见队列。 */
    default void processUnacks(String queueName) {}

    /**
     * 把消息的延迟清零，使其立即可见，但不从队列取出。
     *
     * @return 消息存在并已更新时返回 true
     */
    boolean resetOffsetTime(String queueName, String id);

    /**
     * 把消息推迟指定秒数后再可见。消息不存在时会创建。
     */
    default boolean postpone(
            String queueName, String messageId, int priority, long postponeDurationInSeconds) {
        remove(queueName, messageId);
        push(queueName, messageId, priority, postponeDurationInSeconds);
        return true;
    }

    /** 消息是否还在队列中，包含已取出但未确认的消息。 */
    boolean containsMessage(String queueName, String messageId);

    /**
     * 按计划可见时间返回前 {@code count} 个消息 ID，包含尚未到可见时间的消息。
     */
    default List<String> peekFirstIds(String queueName, int count) {
        return List.of();
    }
}
