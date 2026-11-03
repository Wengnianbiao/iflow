package com.helianhealth.iflow.core.lock;

import com.helianhealth.iflow.core.config.IflowProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 工作流执行锁服务，通过组合 {@link Lock} 隔离底层锁实现。
 *
 * <p>普通启动和 Sweeper 限时尝试取锁；必须完成的决策和状态更新等待取锁。
 */
@Service
public class ExecutionLockService {

    private final Lock lock;
    private final long lockTimeToTry;
    private final long lockLeaseTime;

    public ExecutionLockService(Lock lock) {
        this(new IflowProperties(), lock);
    }

    @Autowired
    public ExecutionLockService(IflowProperties properties, Lock lock) {
        this.lock = lock;
        this.lockTimeToTry = properties.getLockTimeToTry().toMillis();
        this.lockLeaseTime = properties.getLockLeaseTime().toMillis();
    }

    /**
     * 在配置的等待时间内尝试获取带租约的锁。
     *
     * @return 获取成功时返回 {@code true}
     */
    public boolean acquireLock(String lockId) {
        return lock.acquireLock(lockId, lockTimeToTry, lockLeaseTime, TimeUnit.MILLISECONDS);
    }

    /** 等待取得带租约的锁，保留线程的中断状态。 */
    public void waitForLock(String lockId) {
        lock.acquireLock(lockId, lockLeaseTime, TimeUnit.MILLISECONDS);
    }

    /** 释放当前线程持有的锁。 */
    public void releaseLock(String lockId) {
        lock.releaseLock(lockId);
    }
}
