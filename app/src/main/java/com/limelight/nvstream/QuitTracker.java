package com.limelight.nvstream;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Per-host background cleanup; never hold an Activity or send a second cancel. */
public final class QuitTracker {
    private static final ConcurrentHashMap<String, Ticket> pending = new ConcurrentHashMap<>();
    public static final class Ticket {
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile String error;
    }
    public static Ticket begin(String host) {
        Ticket ticket = new Ticket();
        for (;;) {
            Ticket current = pending.putIfAbsent(host, ticket);
            if (current == null) return ticket;
            if (current.done.getCount() != 0) return null;
            // A completed ticket remains briefly so a racing connection can observe its result.
            if (pending.replace(host, current, ticket)) return ticket;
        }
    }
    public static boolean isPending(String host) {
        Ticket ticket = pending.get(host);
        return ticket != null && ticket.done.getCount() != 0;
    }
    public static void finish(String host, Ticket ticket, String error) {
        ticket.error = error;
        ticket.done.countDown();
    }
    public static void await(String host) throws IOException {
        Ticket ticket = pending.get(host);
        if (ticket == null) return;
        try {
            if (!ticket.done.await(20, TimeUnit.SECONDS))
                throw new IOException("上次串流仍在结束中，请稍后重试连接。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("已取消等待主机清理", e);
        }
        pending.remove(host, ticket);
        if (ticket.error != null) throw new IOException(ticket.error);
    }
    private QuitTracker() {}
}
