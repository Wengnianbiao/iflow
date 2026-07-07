package com.helianhealth.agent.core.lock;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 JVM {@link ReentrantLock} 的工作流执行锁默认实现。
 *
 * <p>锁按工作流实例 ID 隔离，并在没有持有者和等待者后清理。该实现只保证单进程内互斥；集群部署时应注入分布式
 * {@link ExecutionLockService} 实现。
 */
public class InMemoryExecutionLockService implements ExecutionLockService {

    static final long DEFAULT_TIME_TO_TRY_MILLIS = 500;

    private final ConcurrentMap<String, LockEntry> locks = new ConcurrentHashMap<>();
    private final long timeToTryMillis;

    public InMemoryExecutionLockService() {
        this(DEFAULT_TIME_TO_TRY_MILLIS);
    }

    public InMemoryExecutionLockService(long timeToTryMillis) {
        if (timeToTryMillis < 0) {
            throw new IllegalArgumentException("timeToTryMillis must not be negative");
        }
        this.timeToTryMillis = timeToTryMillis;
    }

    @Override
    public boolean acquireLock(String lockId) {
        String normalizedLockId = requireLockId(lockId);
        LockEntry entry = retain(normalizedLockId);
        boolean acquired = false;
        try {
            acquired =
                    timeToTryMillis == 0
                            ? entry.lock.tryLock()
                            : entry.lock.tryLock(timeToTryMillis, TimeUnit.MILLISECONDS);
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (!acquired) {
                releaseReference(normalizedLockId, entry);
            }
        }
    }

    @Override
    public void releaseLock(String lockId) {
        String normalizedLockId = requireLockId(lockId);
        LockEntry entry = locks.get(normalizedLockId);
        if (entry == null || !entry.lock.isHeldByCurrentThread()) {
            return;
        }
        entry.lock.unlock();
        releaseReference(normalizedLockId, entry);
    }

    private LockEntry retain(String lockId) {
        return locks.compute(
                lockId,
                (key, current) -> {
                    LockEntry entry = current != null ? current : new LockEntry();
                    entry.references++;
                    return entry;
                });
    }

    private void releaseReference(String lockId, LockEntry expected) {
        locks.computeIfPresent(
                lockId,
                (key, current) -> {
                    if (!Objects.equals(current, expected)) {
                        return current;
                    }
                    current.references--;
                    return current.references == 0 ? null : current;
                });
    }

    private String requireLockId(String lockId) {
        if (lockId == null || lockId.isBlank()) {
            throw new IllegalArgumentException("lockId must not be blank");
        }
        return lockId;
    }

    int lockCount() {
        return locks.size();
    }

    private static final class LockEntry {

        private final ReentrantLock lock = new ReentrantLock(true);
        private int references;
    }
}
