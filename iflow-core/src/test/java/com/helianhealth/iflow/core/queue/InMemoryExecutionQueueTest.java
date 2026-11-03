package com.helianhealth.iflow.core.queue;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryExecutionQueueTest {

    @Test
    void delaysMessagesWithoutOccupyingWorkersAndDeduplicatesWhileWaiting() throws Exception {
        InMemoryQueueDAO dao = new InMemoryQueueDAO();
        CountDownLatch immediate = new CountDownLatch(1);
        CountDownLatch delayed = new CountDownLatch(1);
        long submitted = System.nanoTime();
        try (ExecutorService worker = Executors.newSingleThreadExecutor();
                InMemoryExecutionQueue queue = new InMemoryExecutionQueue(dao, worker)) {
            assertTrue(queue.submitTask("HTTP", "later", 1, id -> delayed.countDown()));
            assertFalse(queue.submitTask("HTTP", "later", 1, id -> fail("duplicate")));
            assertTrue(queue.submitTask("HTTP", "now", id -> immediate.countDown()));
            assertTrue(immediate.await(500, TimeUnit.MILLISECONDS));
            assertFalse(delayed.await(100, TimeUnit.MILLISECONDS));
            assertTrue(dao.containsMessage("HTTP", "later"));
            assertTrue(delayed.await(3, TimeUnit.SECONDS));
            assertTrue(System.nanoTime() - submitted >= TimeUnit.SECONDS.toNanos(1));
        }
        assertFalse(dao.containsMessage("HTTP", "later"));
    }

    @Test
    void keepsAcceptedDelayedMessagesWhenWorkerPoolTemporarilyRejectsDispatch() throws Exception {
        InMemoryQueueDAO dao = new InMemoryQueueDAO();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger submissions = new AtomicInteger();
        try (ExecutorService worker = Executors.newSingleThreadExecutor();
                InMemoryExecutionQueue queue = new InMemoryExecutionQueue(dao, command -> {
                    if (submissions.incrementAndGet() == 1) {
                        throw new RejectedExecutionException("temporarily full");
                    }
                    worker.execute(command);
                })) {
            assertTrue(queue.submitTask("HTTP", "later", 1, id -> completed.countDown()));
            assertTrue(completed.await(3, TimeUnit.SECONDS));
            assertEquals(2, submissions.get());
        }
        assertFalse(dao.containsMessage("HTTP", "later"));
    }

    @Test
    void closedQueueRejectsAdmissionAndNegativeDelaysAreInvalid() {
        InMemoryQueueDAO dao = new InMemoryQueueDAO();
        InMemoryExecutionQueue queue = new InMemoryExecutionQueue(dao, Runnable::run);
        assertThrows(IllegalArgumentException.class,
                () -> queue.submitTask("HTTP", "task", -1, id -> fail("invalid delay")));
        queue.close();
        assertThrows(RejectedExecutionException.class,
                () -> queue.submitTask("HTTP", "task", id -> fail("closed")));
        assertFalse(dao.containsMessage("HTTP", "task"));
    }

    @Test
    void deduplicatesQueuedAndRunningTasksAndAcknowledgesAfterExecution() {
        InMemoryQueueDAO dao = new InMemoryQueueDAO();
        ManualExecutor worker = new ManualExecutor();
        InMemoryExecutionQueue queue = new InMemoryExecutionQueue(dao, worker);
        List<String> executions = new ArrayList<>();
        assertTrue(queue.submitTask("HTTP", "task", id -> {
            assertTrue(dao.containsMessage("HTTP", id));
            assertEquals(0, dao.getSize("HTTP"), "a running message must have been popped");
            assertFalse(queue.submitTask("HTTP", id, executions::add));
            executions.add(id);
        }));
        assertFalse(queue.submitTask("HTTP", "task", executions::add));
        assertEquals(1, worker.actions.size());
        worker.runNext();
        assertEquals(List.of("task"), executions);
        assertFalse(dao.containsMessage("HTTP", "task"));
        assertTrue(queue.submitTask("HTTP", "task", executions::add));
        worker.runNext();
        assertEquals(List.of("task", "task"), executions);
    }

    @Test
    void consumesMessageIdsFromQueueInPriorityOrder() {
        InMemoryQueueDAO dao = new InMemoryQueueDAO();
        ManualExecutor worker = new ManualExecutor();
        InMemoryExecutionQueue queue = new InMemoryExecutionQueue(dao, worker);
        List<String> executions = new ArrayList<>();
        queue.submitTask("HTTP", "first", executions::add);
        queue.submitTask("HTTP", "second", executions::add);
        dao.push("HTTP", "second", 99, 0);

        worker.runNext();
        worker.runNext();

        assertEquals(List.of("second", "first"), executions);
        assertEquals(0, dao.getSize("HTTP"));
    }

    @Test
    void rollsBackRejectedAdmissionWithoutRemovingEarlierMessages() {
        InMemoryQueueDAO dao = new InMemoryQueueDAO();
        ManualExecutor worker = new ManualExecutor();
        int[] submissions = {0};
        InMemoryExecutionQueue queue = new InMemoryExecutionQueue(dao, action -> {
            if (++submissions[0] == 2) {
                throw new RejectedExecutionException("full");
            }
            worker.execute(action);
        });
        List<String> executions = new ArrayList<>();
        queue.submitTask("HTTP", "accepted", executions::add);
        assertThrows(RejectedExecutionException.class,
                () -> queue.submitTask("HTTP", "rejected", executions::add));
        assertFalse(dao.containsMessage("HTTP", "rejected"));
        assertTrue(dao.containsMessage("HTTP", "accepted"));
        worker.runNext();
        assertEquals(List.of("accepted"), executions);
        assertTrue(queue.submitTask("HTTP", "rejected", executions::add));
        worker.runNext();
        assertEquals(List.of("accepted", "rejected"), executions);
    }

    @Test
    void releasesMessageAfterHandlerFailure() {
        InMemoryQueueDAO dao = new InMemoryQueueDAO();
        ManualExecutor worker = new ManualExecutor();
        InMemoryExecutionQueue queue = new InMemoryExecutionQueue(dao, worker);
        RuntimeException failure = new IllegalStateException("failed");
        queue.submitTask("HTTP", "task", id -> { throw failure; });
        assertSame(failure, assertThrows(RuntimeException.class, worker::runNext));
        assertFalse(dao.containsMessage("HTTP", "task"));
        assertTrue(queue.submitTask("HTTP", "task", id -> { }));
        worker.runNext();
    }

    @Test
    void redeliveredLeaseDoesNotConsumeTheOnlyWakeupForANewTask() {
        InMemoryQueueDAO dao = new InMemoryQueueDAO(java.time.Duration.ZERO, java.time.Clock.systemUTC());
        dao.push("HTTP", "expired", 0);
        dao.pop("HTTP", 1, 0);
        ManualExecutor worker = new ManualExecutor();
        InMemoryExecutionQueue queue = new InMemoryExecutionQueue(dao, worker);
        List<String> executions = new ArrayList<>();
        queue.submitTask("HTTP", "new", id -> {
            if (id.equals("expired")) {
                throw new IllegalStateException("old task failed");
            }
            executions.add(id);
        });

        assertThrows(IllegalStateException.class, worker::runNext);

        assertEquals(List.of("new"), executions);
        assertFalse(dao.containsMessage("HTTP", "new"));
        assertFalse(dao.containsMessage("HTTP", "expired"));
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> actions = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) { actions.add(command); }

        private void runNext() { actions.remove().run(); }
    }
}
