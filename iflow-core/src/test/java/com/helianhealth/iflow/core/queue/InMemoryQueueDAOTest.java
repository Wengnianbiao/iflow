package com.helianhealth.iflow.core.queue;

import com.helianhealth.iflow.core.dao.QueueDAO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryQueueDAOTest {

    @Test
    void popsByPriorityAndKeepsMessageUntilAck() {
        QueueDAO queue = new InMemoryQueueDAO();
        queue.push("tasks", "low", 1, 0);
        queue.push("tasks", "high", 9, 0);
        queue.push("tasks", "middle", 5, 0);

        assertEquals(List.of("high"), queue.pop("tasks", 1, 0));
        assertEquals(List.of("middle", "low"), queue.pop("tasks", 10, 0));
        assertEquals(0, queue.getSize("tasks"));
        assertTrue(queue.containsMessage("tasks", "middle"));
        assertEquals(3L, queue.queuesDetailVerbose()
                .get("tasks").get(InMemoryQueueDAO.MEMORY_SHARD).get("uacked"));
        assertTrue(queue.ack("tasks", "high"));
        assertFalse(queue.ack("tasks", "missing"));
        assertFalse(queue.containsMessage("tasks", "high"));
    }

    @Test
    void delaysVisibilityUntilOffsetElapsesOrIsReset() {
        MutableClock clock = new MutableClock(1_000_000);
        QueueDAO queue = new InMemoryQueueDAO(Duration.ofSeconds(30), clock);
        queue.push("tasks", "later", 0, 10);

        assertTrue(queue.pop("tasks", 1, 0).isEmpty());
        assertEquals(1, queue.getSize("tasks"));
        assertEquals(List.of("later"), queue.peekFirstIds("tasks", 5));
        assertFalse(queue.pushIfNotExists("tasks", "later", 0));
        assertTrue(queue.pushIfNotExists("tasks", "other", 0));

        clock.advance(10_000);
        assertEquals(List.of("other", "later"), queue.pop("tasks", 2, 0));

        queue.push("tasks", "postponed", 3, 0);
        assertTrue(queue.postpone("tasks", "postponed", 3, 5));
        assertTrue(queue.pop("tasks", 1, 0).isEmpty());
        assertTrue(queue.resetOffsetTime("tasks", "postponed"));
        assertEquals(List.of("postponed"), queue.pop("tasks", 1, 0));
    }

    @Test
    void returnsUnackedMessageAfterLeaseAndHonorsShorterTimeout() {
        MutableClock clock = new MutableClock(5_000);
        QueueDAO queue = new InMemoryQueueDAO(Duration.ofMillis(1_000), clock);
        queue.push("tasks", "retry", 0);
        assertEquals(List.of("retry"), queue.pop("tasks", 1, 0));
        assertTrue(queue.pop("tasks", 1, 0).isEmpty());

        assertTrue(queue.setUnackTimeout("tasks", "retry", 5_000));
        clock.advance(1_000);
        queue.processUnacks("tasks");
        assertTrue(queue.pop("tasks", 1, 0).isEmpty());

        assertFalse(queue.setUnackTimeoutIfShorter("tasks", "retry", 10_000));
        assertTrue(queue.setUnackTimeoutIfShorter("tasks", "retry", 500));
        clock.advance(500);
        queue.processUnacks("tasks");
        assertEquals(List.of("retry"), queue.pop("tasks", 1, 0));

        clock.advance(1_000);
        queue.processUnacks("tasks");
        assertEquals(List.of("retry"), queue.pop("tasks", 1, 0));
        queue.flush("tasks");
        assertFalse(queue.containsMessage("tasks", "retry"));
        assertEquals(0, queue.getSize("tasks"));
    }

    @Test
    void waitsForAMessageThatArrivesBeforeTimeout() throws Exception {
        QueueDAO queue = new InMemoryQueueDAO();
        CountDownLatch started = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            await(started);
            queue.push("tasks", "arrived", 0);
        });
        producer.start();
        started.countDown();

        assertEquals(List.of("arrived"), queue.pop("tasks", 1, 1_000));
        producer.join(1_000);
    }

    @Test
    void rejectsBlankNamesAndNegativeLease() {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryQueueDAO().push(" ", "id", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryQueueDAO(Duration.ofMillis(-1), Clock.systemUTC()));
    }

    private static void await(CountDownLatch signal) {
        try {
            if (!signal.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private static final class MutableClock extends Clock {
        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        private void advance(long delta) {
            millis += delta;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }
}
