package com.helianhealth.iflow.core.lock;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * JVM 内存租约锁，租约到期后唤醒等待线程，允许重新取锁。
 *
 * <p>租约从首次取得锁时计时，重入不续租。到期只撤销锁的持有权，不停止原持有线程。
 */
public class InMemoryLock implements Lock {

    private static final ScheduledThreadPoolExecutor LEASE_EXPIRATIONS = createLeaseScheduler();

    private final Map<String, LockEntry> locks = new HashMap<>();

    @Override
    public void acquireLock(String lockId, long leaseTime, TimeUnit unit) {
        acquireLock(lockId, 0, leaseTime, unit, false);
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        return acquireLock(lockId, timeToTry, leaseTime, unit, true);
    }

    private boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit,
            boolean timed) {
        requireLockId(lockId);
        if (timeToTry < 0 || leaseTime <= 0) {
            throw new IllegalArgumentException("timeToTry must not be negative and leaseTime must be positive");
        }
        Objects.requireNonNull(unit, "unit");
        long waitNanos = unit.toNanos(timeToTry);
        long leaseNanos = unit.toNanos(leaseTime);
        long startTime = System.nanoTime();
        Thread currentThread = Thread.currentThread();
        boolean interrupted = false;
        try {
            synchronized (locks) {
                while (true) {
                    if (timed && currentThread.isInterrupted()) {
                        return false;
                    }
                    long now = System.nanoTime();
                    LockEntry entry = locks.get(lockId);
                    if (entry != null && now - entry.expiresAtNanos >= 0) {
                        removeLock(lockId, entry);
                        entry = null;
                    }
                    if (entry == null) {
                        LockEntry acquired = new LockEntry(currentThread, now + leaseNanos);
                        acquired.expiration = LEASE_EXPIRATIONS.schedule(
                                () -> expireLock(lockId, acquired), leaseNanos, TimeUnit.NANOSECONDS);
                        locks.put(lockId, acquired);
                        return true;
                    }
                    if (entry.owner == currentThread) {
                        entry.holdCount++;
                        return true;
                    }
                    long nanosToWait = entry.expiresAtNanos - now;
                    if (timed) {
                        long remaining = waitNanos - (now - startTime);
                        if (remaining <= 0) {
                            return false;
                        }
                        nanosToWait = Math.min(nanosToWait, remaining);
                    }
                    try {
                        TimeUnit.NANOSECONDS.timedWait(locks, nanosToWait);
                    } catch (InterruptedException error) {
                        interrupted = true;
                        if (timed) {
                            return false;
                        }
                    }
                    if (timed && System.nanoTime() - startTime >= waitNanos) {
                        return false;
                    }
                }
            }
        } finally {
            if (interrupted) {
                currentThread.interrupt();
            }
        }
    }

    @Override
    public void releaseLock(String lockId) {
        requireLockId(lockId);
        synchronized (locks) {
            LockEntry entry = locks.get(lockId);
            if (entry == null || entry.owner != Thread.currentThread()) {
                return;
            }
            if (--entry.holdCount == 0 || System.nanoTime() - entry.expiresAtNanos >= 0) {
                removeLock(lockId, entry);
            }
        }
    }

    private void expireLock(String lockId, LockEntry expected) {
        synchronized (locks) {
            removeLock(lockId, expected);
        }
    }

    /** 调用方持有 locks 的监视器，旧租约任务只能移除对应的持有记录。 */
    private void removeLock(String lockId, LockEntry expected) {
        if (locks.remove(lockId, expected)) {
            expected.expiration.cancel(false);
            locks.notifyAll();
        }
    }

    private void requireLockId(String lockId) {
        if (lockId == null || lockId.isBlank()) {
            throw new IllegalArgumentException("lockId must not be blank");
        }
    }

    int lockCount() {
        synchronized (locks) {
            return locks.size();
        }
    }

    private static ScheduledThreadPoolExecutor createLeaseScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "iflow-lock-lease");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static final class LockEntry {

        private final Thread owner;
        private final long expiresAtNanos;
        private int holdCount = 1;
        private ScheduledFuture<?> expiration;

        private LockEntry(Thread owner, long expiresAtNanos) {
            this.owner = owner;
            this.expiresAtNanos = expiresAtNanos;
        }
    }
}
