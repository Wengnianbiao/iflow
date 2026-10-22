package com.helianhealth.iflow.core.queue;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryExecutionQueueTest {

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
