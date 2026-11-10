package com.devisri.reposcout.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Protects the free AI quota on the public demo: each visitor (by IP) and the whole server may only ask
 * so many questions per hour. Sliding one-hour window, kept in memory.
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MS = 60 * 60 * 1000L;

    private final int perVisitor;
    private final int total;
    private final LongSupplier clock;

    private final Map<String, Deque<Long>> byVisitor = new ConcurrentHashMap<>();
    private final Deque<Long> all = new ArrayDeque<>();

    @Autowired
    public RateLimiter(@Value("${reposcout.limits.questions-per-visitor-per-hour:10}") int perVisitor,
                       @Value("${reposcout.limits.questions-per-hour:60}") int total) {
        this(perVisitor, total, System::currentTimeMillis);
    }

    RateLimiter(int perVisitor, int total, LongSupplier clock) {
        this.perVisitor = perVisitor;
        this.total = total;
        this.clock = clock;
    }

    /** Thrown when a limit is reached; the message is shown to the user. */
    public static class LimitReachedException extends RuntimeException {
        public LimitReachedException(String message) {
            super(message);
        }
    }

    /** Records one question for this visitor, or throws if a limit is reached. */
    public synchronized void check(String visitor) {
        long now = clock.getAsLong();
        prune(all, now);
        Deque<Long> mine = byVisitor.computeIfAbsent(visitor, v -> new ArrayDeque<>());
        prune(mine, now);

        if (mine.size() >= perVisitor) {
            throw new LimitReachedException("You've asked " + perVisitor
                    + " questions in the last hour. This free demo limits each visitor; please try again later.");
        }
        if (all.size() >= total) {
            throw new LimitReachedException(
                    "The demo is very busy right now and its free AI quota is used up. Please try again later.");
        }
        mine.addLast(now);
        all.addLast(now);

        // Forget visitors with nothing left in the window so the map doesn't grow forever.
        byVisitor.values().removeIf(q -> {
            prune(q, now);
            return q.isEmpty() && q != mine;
        });
    }

    private static void prune(Deque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
            times.removeFirst();
        }
    }
}
