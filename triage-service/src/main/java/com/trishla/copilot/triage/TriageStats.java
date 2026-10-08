package com.trishla.copilot.triage;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** In-memory counters used to measure pipeline throughput and processing time. */
@Component
public class TriageStats {

    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong totalProcessingNanos = new AtomicLong();
    private volatile long firstEventMillis = 0;
    private volatile long lastEventMillis = 0;

    public void record(long processingNanos) {
        long now = System.currentTimeMillis();
        if (processed.getAndIncrement() == 0) {
            firstEventMillis = now;
        }
        lastEventMillis = now;
        totalProcessingNanos.addAndGet(processingNanos);
    }

    public long processed() {
        return processed.get();
    }

    /** Average events per second between the first and last event since the last reset. */
    public double throughputPerSecond() {
        long n = processed.get();
        long elapsed = lastEventMillis - firstEventMillis;
        return (n < 2 || elapsed <= 0) ? 0.0 : n * 1000.0 / elapsed;
    }

    public double avgProcessingMillis() {
        long n = processed.get();
        return n == 0 ? 0.0 : totalProcessingNanos.get() / 1_000_000.0 / n;
    }

    public void reset() {
        processed.set(0);
        totalProcessingNanos.set(0);
        firstEventMillis = 0;
        lastEventMillis = 0;
    }
}
