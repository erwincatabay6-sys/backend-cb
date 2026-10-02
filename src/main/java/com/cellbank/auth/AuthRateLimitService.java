package com.cellbank.auth;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class AuthRateLimitService {

    private static final int MAX_ENTRIES = 10_000;

    private static final long CLEANUP_INTERVAL_NANOS =
            Duration.ofMinutes(1).toNanos();

    private final Map<String, Window> windows = new HashMap<>();

    private long lastCleanupNanos = System.nanoTime();

    /**
     * Returns 0 when allowed.
     * Otherwise, returns the number of seconds before retrying.
     *
     * Each key must consistently use the same limit and duration.
     */
    public synchronized long checkAndConsume(
            String key,
            int maxRequests,
            Duration duration) {

        if (key == null || key.isBlank()
                || maxRequests < 1
                || duration == null
                || duration.isZero()
                || duration.isNegative()) {

            throw new IllegalArgumentException(
                    "Invalid rate-limit configuration."
            );
        }

        long now = System.nanoTime();
        long durationNanos = duration.toNanos();

        if (now - lastCleanupNanos >= CLEANUP_INTERVAL_NANOS) {
            windows.entrySet().removeIf(entry ->
                    entry.getValue().isExpired(now)
            );

            lastCleanupNanos = now;
        }

        Window window = windows.get(key);

        if (window != null && window.isExpired(now)) {
            windows.remove(key);
            window = null;
        }

        if (window == null) {
            // Keep memory bounded without discarding active limits.
            if (windows.size() >= MAX_ENTRIES) {
                return 60;
            }

            window = new Window(now, durationNanos);
            windows.put(key, window);
        }

        if (window.requests >= maxRequests) {
            long remainingNanos =
                    window.durationNanos - (now - window.startedAtNanos);

            return Math.max(
                    1L,
                    (long) Math.ceil(remainingNanos / 1_000_000_000.0)
            );
        }

        window.requests++;
        return 0;
    }

    private static final class Window {

        private final long startedAtNanos;
        private final long durationNanos;

        private int requests;

        private Window(long startedAtNanos, long durationNanos) {
            this.startedAtNanos = startedAtNanos;
            this.durationNanos = durationNanos;
        }

        private boolean isExpired(long now) {
            return now - startedAtNanos >= durationNanos;
        }
    }
}
