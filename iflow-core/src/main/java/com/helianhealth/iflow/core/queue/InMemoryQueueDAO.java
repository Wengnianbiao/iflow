package com.helianhealth.iflow.core.queue;

import com.helianhealth.iflow.core.dao.QueueDAO;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JVM 内存队列，是框架默认的 {@link QueueDAO}。
 *
 * <p>语义对齐 Conductor：同一队列内消息 ID 唯一；{@code pop} 后进入未确认状态，直到 ack、remove、
 * 再次 push，或未确认租约到期。延迟和租约只存在于当前进程，重启后不会恢复。
 */
public class InMemoryQueueDAO implements QueueDAO {

    static final String MEMORY_SHARD = "memory";
    private static final Duration DEFAULT_UNACK_TIMEOUT = Duration.ofSeconds(60);

    private final ConcurrentMap<String, QueueState> queues = new ConcurrentHashMap<>();
    private final long unackTimeoutMillis;
    private final Clock clock;

    public InMemoryQueueDAO() {
        this(DEFAULT_UNACK_TIMEOUT, Clock.systemUTC());
    }

    public InMemoryQueueDAO(Duration unackTimeout, Clock clock) {
        if (unackTimeout == null || unackTimeout.isNegative()) {
            throw new IllegalArgumentException("unackTimeout 不能为负");
        }
        this.unackTimeoutMillis = unackTimeout.toMillis();
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void push(String queueName, String id, long offsetTimeInSecond) {
        push(queueName, id, 0, offsetTimeInSecond);
    }

    @Override
    public void push(String queueName, String id, int priority, long offsetTimeInSecond) {
        offer(queueName, id, priority, secondsToMillis(offsetTimeInSecond), false);
    }

    @Override
    public void push(String queueName, String id, int priority, Duration offsetTime) {
        Objects.requireNonNull(offsetTime, "offsetTime");
        offer(queueName, id, priority, offsetTime.toMillis(), false);
    }

    @Override
    public boolean pushIfNotExists(String queueName, String id, long offsetTimeInSecond) {
        return pushIfNotExists(queueName, id, 0, offsetTimeInSecond);
    }

    @Override
    public boolean pushIfNotExists(
            String queueName, String id, int priority, long offsetTimeInSecond) {
        return offer(queueName, id, priority, secondsToMillis(offsetTimeInSecond), true);
    }

    @Override
    public List<String> pop(String queueName, int count, int timeout) {
        List<Entry> popped = popEntries(queueName, count, timeout);
        List<String> ids = new ArrayList<>(popped.size());
        for (Entry entry : popped) {
            ids.add(entry.id);
        }
        return ids;
    }

    @Override
    public void remove(String queueName, String messageId) {
        QueueState state = queues.get(requireText(queueName, "queueName"));
        if (state == null) {
            return;
        }
        state.lock.lock();
        try {
            state.messages.remove(requireText(messageId, "messageId"));
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public int getSize(String queueName) {
        QueueState state = queues.get(requireText(queueName, "queueName"));
        if (state == null) {
            return 0;
        }
        state.lock.lock();
        try {
            releaseExpiredUnacks(state, clock.millis());
            int size = 0;
            for (Entry entry : state.messages.values()) {
                if (!entry.popped) {
                    size++;
                }
            }
            return size;
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public boolean ack(String queueName, String messageId) {
        QueueState state = queues.get(requireText(queueName, "queueName"));
        if (state == null) {
            return false;
        }
        state.lock.lock();
        try {
            return state.messages.remove(requireText(messageId, "messageId")) != null;
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public boolean setUnackTimeout(String queueName, String messageId, long unackTimeout) {
        return updateLease(queueName, messageId, unackTimeout, false);
    }

    @Override
    public boolean setUnackTimeoutIfShorter(
            String queueName, String messageId, long unackTimeout) {
        return updateLease(queueName, messageId, unackTimeout, true);
    }

    @Override
    public void flush(String queueName) {
        QueueState state = queues.get(requireText(queueName, "queueName"));
        if (state == null) {
            return;
        }
        state.lock.lock();
        try {
            state.messages.clear();
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public Map<String, Long> queuesDetail() {
        Map<String, Long> detail = new HashMap<>();
        for (Map.Entry<String, QueueState> queue : queues.entrySet()) {
            detail.put(queue.getKey(), (long) getSize(queue.getKey()));
        }
        return detail;
    }

    @Override
    public Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose() {
        Map<String, Map<String, Map<String, Long>>> detail = new HashMap<>();
        for (Map.Entry<String, QueueState> queue : queues.entrySet()) {
            QueueState state = queue.getValue();
            state.lock.lock();
            long waiting = 0;
            long unacked = 0;
            try {
                releaseExpiredUnacks(state, clock.millis());
                for (Entry entry : state.messages.values()) {
                    if (entry.popped) {
                        unacked++;
                    } else {
                        waiting++;
                    }
                }
            } finally {
                state.lock.unlock();
            }
            Map<String, Long> shard = new HashMap<>();
            shard.put("size", waiting);
            shard.put("uacked", unacked);
            detail.put(queue.getKey(), Map.of(MEMORY_SHARD, shard));
        }
        return detail;
    }

    @Override
    public void processUnacks(String queueName) {
        QueueState state = queues.get(requireText(queueName, "queueName"));
        if (state == null) {
            return;
        }
        state.lock.lock();
        try {
            if (releaseExpiredUnacks(state, clock.millis())) {
                state.available.signalAll();
            }
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public boolean resetOffsetTime(String queueName, String id) {
        QueueState state = queues.get(requireText(queueName, "queueName"));
        if (state == null) {
            return false;
        }
        state.lock.lock();
        try {
            Entry entry = state.messages.get(requireText(id, "id"));
            if (entry == null) {
                return false;
            }
            entry.deliveryTime = clock.millis();
            entry.popped = false;
            state.available.signalAll();
            return true;
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public boolean postpone(
            String queueName, String messageId, int priority, long postponeDurationInSeconds) {
        push(queueName, messageId, priority, postponeDurationInSeconds);
        return true;
    }

    @Override
    public boolean containsMessage(String queueName, String messageId) {
        QueueState state = queues.get(requireText(queueName, "queueName"));
        if (state == null) {
            return false;
        }
        state.lock.lock();
        try {
            return state.messages.containsKey(requireText(messageId, "messageId"));
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public List<String> peekFirstIds(String queueName, int count) {
        if (count <= 0) {
            return List.of();
        }
        QueueState state = queues.get(requireText(queueName, "queueName"));
        if (state == null) {
            return List.of();
        }
        state.lock.lock();
        try {
            List<Entry> ordered = new ArrayList<>(state.messages.values());
            ordered.sort(Comparator.comparingLong((Entry entry) -> entry.deliveryTime)
                    .thenComparingLong(entry -> entry.sequence));
            List<String> ids = new ArrayList<>();
            for (Entry entry : ordered) {
                if (ids.size() == count) {
                    break;
                }
                ids.add(entry.id);
            }
            return ids;
        } finally {
            state.lock.unlock();
        }
    }

    private boolean offer(
            String queueName,
            String id,
            int priority,
            long offsetMillis,
            boolean onlyIfAbsent) {
        QueueState state = state(queueName);
        state.lock.lock();
        try {
            String messageId = requireText(id, "id");
            Entry existing = state.messages.get(messageId);
            if (existing != null && onlyIfAbsent) {
                return false;
            }
            long now = clock.millis();
            Entry entry = existing != null ? existing : new Entry(messageId, state.nextSequence++);
            entry.priority = priority;
            entry.deliveryTime = now + Math.max(offsetMillis, 0);
            entry.popped = false;
            entry.unackUntil = 0;
            if (existing == null) {
                state.messages.put(messageId, entry);
            }
            state.available.signalAll();
            return existing == null;
        } finally {
            state.lock.unlock();
        }
    }

    private List<Entry> popEntries(String queueName, int count, int timeout) {
        if (count <= 0) {
            return List.of();
        }
        int waitMillis = Math.max(timeout, 0);
        QueueState state = state(queueName);
        long waitDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        state.lock.lock();
        try {
            while (true) {
                long now = clock.millis();
                releaseExpiredUnacks(state, now);
                List<Entry> ready = readyMessages(state, count, now);
                if (!ready.isEmpty() || waitMillis == 0 || System.nanoTime() >= waitDeadline) {
                    return markPopped(ready, now);
                }
                long remaining = waitDeadline - System.nanoTime();
                if (remaining <= 0) {
                    return List.of();
                }
                try {
                    // 分段等待，以便未确认租约到期后能重新可见，而不必睡满整个超时。
                    long slice = Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(200));
                    state.available.await(slice, TimeUnit.NANOSECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    long interruptedAt = clock.millis();
                    releaseExpiredUnacks(state, interruptedAt);
                    return markPopped(readyMessages(state, count, interruptedAt), interruptedAt);
                }
            }
        } finally {
            state.lock.unlock();
        }
    }

    private boolean updateLease(
            String queueName, String messageId, long unackTimeout, boolean onlyIfShorter) {
        if (unackTimeout < 0) {
            throw new IllegalArgumentException("unackTimeout 不能为负");
        }
        QueueState state = queues.get(requireText(queueName, "queueName"));
        if (state == null) {
            return false;
        }
        state.lock.lock();
        try {
            Entry entry = state.messages.get(requireText(messageId, "messageId"));
            if (entry == null) {
                return false;
            }
            long now = clock.millis();
            long nextVisibleAt = now + unackTimeout;
            long currentVisibleAt = entry.popped ? entry.unackUntil : entry.deliveryTime;
            if (onlyIfShorter && nextVisibleAt >= currentVisibleAt) {
                return false;
            }
            if (unackTimeout == 0) {
                entry.deliveryTime = now;
                entry.popped = false;
                entry.unackUntil = 0;
                state.available.signalAll();
                return true;
            }
            entry.popped = true;
            entry.unackUntil = nextVisibleAt;
            return true;
        } finally {
            state.lock.unlock();
        }
    }

    private List<Entry> readyMessages(QueueState state, int count, long now) {
        List<Entry> ready = new ArrayList<>();
        for (Entry entry : state.messages.values()) {
            if (!entry.popped && entry.deliveryTime <= now) {
                ready.add(entry);
            }
        }
        ready.sort(Comparator.comparingInt((Entry entry) -> entry.priority).reversed()
                .thenComparingLong(entry -> entry.deliveryTime)
                .thenComparingLong(entry -> entry.sequence));
        if (ready.size() > count) {
            return new ArrayList<>(ready.subList(0, count));
        }
        return ready;
    }

    private List<Entry> markPopped(List<Entry> ready, long now) {
        for (Entry entry : ready) {
            entry.popped = true;
            entry.unackUntil = now + unackTimeoutMillis;
        }
        return ready;
    }

    private boolean releaseExpiredUnacks(QueueState state, long now) {
        boolean released = false;
        for (Entry entry : state.messages.values()) {
            if (entry.popped && entry.unackUntil <= now) {
                entry.popped = false;
                released = true;
            }
        }
        return released;
    }

    private QueueState state(String queueName) {
        String name = requireText(queueName, "queueName");
        return queues.computeIfAbsent(name, key -> new QueueState());
    }

    private static long secondsToMillis(long seconds) {
        if (seconds <= 0) {
            return 0;
        }
        return Math.multiplyExact(seconds, 1000L);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }

    private static final class QueueState {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition available = lock.newCondition();
        private final Map<String, Entry> messages = new LinkedHashMap<>();
        private long nextSequence;
    }

    private static final class Entry {
        private final String id;
        private final long sequence;
        private int priority;
        private long deliveryTime;
        private boolean popped;
        private long unackUntil;

        private Entry(String id, long sequence) {
            this.id = id;
            this.sequence = sequence;
        }
    }
}
