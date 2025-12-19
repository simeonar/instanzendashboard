package org.example.scanner;

/**
 * Immutable snapshot of scan progress.
 */
public class ScanProgressSnapshot {
    private final boolean inProgress;
    private final long startedAtEpochMs;
    private final int total;
    private final int completed;
    private final String currentAddress;

    public ScanProgressSnapshot(boolean inProgress, long startedAtEpochMs, int total, int completed, String currentAddress) {
        this.inProgress = inProgress;
        this.startedAtEpochMs = startedAtEpochMs;
        this.total = total;
        this.completed = completed;
        this.currentAddress = currentAddress;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public long getStartedAtEpochMs() {
        return startedAtEpochMs;
    }

    public int getTotal() {
        return total;
    }

    public int getCompleted() {
        return completed;
    }

    public String getCurrentAddress() {
        return currentAddress;
    }

    public long getElapsedMs(long nowEpochMs) {
        if (!inProgress || startedAtEpochMs <= 0) {
            return 0;
        }
        return Math.max(0, nowEpochMs - startedAtEpochMs);
    }
}
