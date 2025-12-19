package org.example.scanner;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe scan progress tracker.
 */
public class ScanProgressTracker {
    private final AtomicReference<String> currentAddress = new AtomicReference<>(null);
    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicLong startedAtEpochMs = new AtomicLong(0);
    private final AtomicReference<Boolean> inProgress = new AtomicReference<>(false);

    public void start(int totalCount) {
        inProgress.set(true);
        startedAtEpochMs.set(System.currentTimeMillis());
        total.set(Math.max(0, totalCount));
        completed.set(0);
        currentAddress.set(null);
    }

    public void updateCurrent(String address) {
        currentAddress.set(address);
    }

    public void incrementCompleted() {
        completed.incrementAndGet();
    }

    public void finish() {
        currentAddress.set(null);
        inProgress.set(false);
    }

    public ScanProgressSnapshot snapshot() {
        return new ScanProgressSnapshot(
                inProgress.get(),
                startedAtEpochMs.get(),
                total.get(),
                completed.get(),
                currentAddress.get()
        );
    }
}
