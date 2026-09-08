package com.limelight.nvstream;

/** Serializes changes and coalesces queued choices without discarding the last one. */
public final class LatestScaleQueue {
    private Integer pending;
    private boolean running;
    private boolean closed;
    public synchronized boolean offer(int value) {
        if (closed) return false;
        pending = value;
        if (running) return false;
        running = true;
        return true;
    }
    public synchronized Integer next() {
        if (closed || pending == null) { running = false; return null; }
        Integer value = pending;
        pending = null;
        return value;
    }
    public synchronized void close() { closed = true; pending = null; }
}
