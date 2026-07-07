package com.helianhealth.agent.core.lock;

/**
 * 工作流执行锁边界。
 *
 * <p>锁 ID 通常使用工作流实例 ID。实现可以是进程内锁，也可以替换为 Redis 等分布式锁。
 */
public interface ExecutionLockService {

    /**
     * 在实现约定的等待时间内尝试获取锁。
     *
     * @return 获取成功时返回 {@code true}
     */
    boolean acquireLock(String lockId);

    /** 释放当前线程持有的锁。 */
    void releaseLock(String lockId);
}
