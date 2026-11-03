package com.helianhealth.iflow.core.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class InMemoryLockTest {

    private final InMemoryLock lock = new InMemoryLock();

    @Test
    void reentrantLockRequiresMatchingReleasesAndIgnoresOtherThreadsReleasingIt() throws Exception {
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            lock.acquireLock("workflow", 30, TimeUnit.SECONDS);
            try {
                assertTrue(lock.acquireLock("workflow", 0, 30_000, TimeUnit.MILLISECONDS));
                lock.releaseLock("workflow");
                assertEquals(1, lock.lockCount());
                assertFalse(worker.submit(() -> {
                    lock.releaseLock("workflow");
                    return lock.acquireLock("workflow", 10, 30_000, TimeUnit.MILLISECONDS);
                }).get(2, TimeUnit.SECONDS));
                assertTrue(worker.submit(() -> {
                    boolean acquired = lock.acquireLock("another-workflow", 0, 30_000, TimeUnit.MILLISECONDS);
                    if (acquired) {
                        lock.releaseLock("another-workflow");
                    }
                    return acquired;
                }).get(2, TimeUnit.SECONDS));
                assertEquals(1, lock.lockCount());
            } finally {
                lock.releaseLock("workflow");
            }
            assertTrue(worker.submit(() -> {
                boolean acquired = lock.acquireLock("workflow", 0, 30_000, TimeUnit.MILLISECONDS);
                if (acquired) {
                    lock.releaseLock("workflow");
                }
                return acquired;
            }).get(2, TimeUnit.SECONDS));
            lock.releaseLock("workflow");
            assertEquals(0, lock.lockCount());
        }
    }

    @Test
    void interruptingTimedAcquisitionReturnsFalseAndDoesNotLeakReferences() throws Exception {
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            lock.acquireLock("workflow", 30, TimeUnit.SECONDS);
            CountDownLatch entered = new CountDownLatch(1);
            AtomicReference<Thread> waitingThread = new AtomicReference<>();
            try {
                Future<Boolean> result = worker.submit(() -> {
                    waitingThread.set(Thread.currentThread());
                    entered.countDown();
                    assertFalse(lock.acquireLock("workflow", 5, 30, TimeUnit.SECONDS));
                    return Thread.currentThread().isInterrupted();
                });
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                waitingThread.get().interrupt();
                assertTrue(result.get(2, TimeUnit.SECONDS));
                assertEquals(1, lock.lockCount());
            } finally {
                lock.releaseLock("workflow");
            }
            assertEquals(0, lock.lockCount());
        }
    }

    @Test
    void blockingAcquisitionWaitsForOwnerAndRetainsInterruptStatus() throws Exception {
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            lock.acquireLock("workflow", 30, TimeUnit.SECONDS);
            CountDownLatch entered = new CountDownLatch(1);
            AtomicReference<Thread> waitingThread = new AtomicReference<>();
            Future<Boolean> result;
            try {
                result = worker.submit(() -> {
                    waitingThread.set(Thread.currentThread());
                    entered.countDown();
                    lock.acquireLock("workflow", 30, TimeUnit.SECONDS);
                    try {
                        return Thread.currentThread().isInterrupted();
                    } finally {
                        lock.releaseLock("workflow");
                    }
                });
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                waitingThread.get().interrupt();
                assertThrows(TimeoutException.class, () -> result.get(50, TimeUnit.MILLISECONDS));
            } finally {
                lock.releaseLock("workflow");
            }
            assertTrue(result.get(2, TimeUnit.SECONDS));
            assertEquals(0, lock.lockCount());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void timedAcquisitionWithAnExistingInterruptDoesNotAcquireOrLeak(long timeToTry) throws Exception {
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            assertTrue(worker.submit(() -> {
                Thread.currentThread().interrupt();
                assertFalse(lock.acquireLock("workflow", timeToTry, 30_000, TimeUnit.MILLISECONDS));
                return Thread.currentThread().isInterrupted();
            }).get(2, TimeUnit.SECONDS));
        }
        assertEquals(0, lock.lockCount());
    }

    @Test
    void concurrentAcquisitionsStayExclusiveWhileEntriesAreCreatedAndCleaned() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(4)) {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(workers.submit(() -> {
                    assertTrue(start.await(2, TimeUnit.SECONDS));
                    for (int j = 0; j < 200; j++) {
                        lock.acquireLock("workflow", 30, TimeUnit.SECONDS);
                        try {
                            assertEquals(1, active.incrementAndGet());
                            completed.incrementAndGet();
                        } finally {
                            active.decrementAndGet();
                            lock.releaseLock("workflow");
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(5, TimeUnit.SECONDS);
            }
        }
        assertEquals(800, completed.get());
        assertEquals(0, lock.lockCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitingThreadTakesOverExpiredLeaseAndOldOwnerCannotReleaseNewLease(boolean blocking) throws Exception {
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            lock.acquireLock("workflow", 200, TimeUnit.MILLISECONDS);
            CountDownLatch waiting = new CountDownLatch(1);
            CountDownLatch acquired = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try {
                Future<?> nextOwner = workers.submit(() -> {
                    waiting.countDown();
                    if (blocking) {
                        lock.acquireLock("workflow", 5, TimeUnit.SECONDS);
                    } else {
                        assertTrue(lock.acquireLock("workflow", 2, 5, TimeUnit.SECONDS));
                    }
                    try {
                        acquired.countDown();
                        assertTrue(release.await(2, TimeUnit.SECONDS));
                    } finally {
                        lock.releaseLock("workflow");
                    }
                    return null;
                });
                assertTrue(waiting.await(2, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> nextOwner.get(20, TimeUnit.MILLISECONDS));
                assertTrue(acquired.await(2, TimeUnit.SECONDS));

                // 原持有者仍活着且从未释放锁；迟到释放不能影响接管线程。
                lock.releaseLock("workflow");
                assertFalse(workers.submit(() -> lock.acquireLock("workflow", 0, 5, TimeUnit.SECONDS))
                        .get(2, TimeUnit.SECONDS));
                release.countDown();
                nextOwner.get(2, TimeUnit.SECONDS);
                assertEquals(0, lock.lockCount());
            } finally {
                release.countDown();
                lock.releaseLock("workflow");
            }
        }
    }

    @Test
    void releasingEarlyDoesNotLetOldExpirationRemoveALaterLease() throws Exception {
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            lock.acquireLock("workflow", 100, TimeUnit.MILLISECONDS);
            lock.releaseLock("workflow");
            lock.acquireLock("workflow", 5, TimeUnit.SECONDS);
            try {
                assertFalse(worker.submit(() -> lock.acquireLock("workflow", 250, 5_000, TimeUnit.MILLISECONDS))
                        .get(2, TimeUnit.SECONDS));
                assertEquals(1, lock.lockCount());
            } finally {
                lock.releaseLock("workflow");
            }
            assertEquals(0, lock.lockCount());
        }
    }

    @Test
    void reentryDoesNotRenewTheOriginalLease() throws Exception {
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            lock.acquireLock("workflow", 100, TimeUnit.MILLISECONDS);
            lock.acquireLock("workflow", 5, TimeUnit.SECONDS);
            try {
                assertTrue(worker.submit(() -> {
                    boolean acquired = lock.acquireLock("workflow", 2, 5, TimeUnit.SECONDS);
                    if (acquired) {
                        lock.releaseLock("workflow");
                    }
                    return acquired;
                }).get(3, TimeUnit.SECONDS));
            } finally {
                lock.releaseLock("workflow");
                lock.releaseLock("workflow");
            }
            assertEquals(0, lock.lockCount());
        }
    }

    @Test
    void expiredLeaseIsCleanedEvenWhenNobodyAttemptsToAcquireIt() {
        lock.acquireLock("workflow", 50, TimeUnit.MILLISECONDS);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (lock.lockCount() != 0) {
                Thread.sleep(10);
            }
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void rejectsInvalidIdsBeforeCreatingAnEntry(String lockId) {
        assertThrows(IllegalArgumentException.class, () -> lock.acquireLock(lockId, 30, TimeUnit.SECONDS));
        assertThrows(IllegalArgumentException.class,
                () -> lock.acquireLock(lockId, 0, 30_000, TimeUnit.MILLISECONDS));
        assertThrows(IllegalArgumentException.class, () -> lock.releaseLock(lockId));
        assertEquals(0, lock.lockCount());
    }

    @Test
    void rejectsInvalidWaitArgumentsBeforeCreatingAnEntry() {
        assertThrows(IllegalArgumentException.class,
                () -> lock.acquireLock("workflow", -1, 30_000, TimeUnit.MILLISECONDS));
        assertThrows(NullPointerException.class, () -> lock.acquireLock("workflow", 1, 30, null));
        assertThrows(IllegalArgumentException.class,
                () -> lock.acquireLock("workflow", 0, TimeUnit.MILLISECONDS));
        assertThrows(IllegalArgumentException.class,
                () -> lock.acquireLock("workflow", 1, -1, TimeUnit.MILLISECONDS));
        assertEquals(0, lock.lockCount());
    }
}
