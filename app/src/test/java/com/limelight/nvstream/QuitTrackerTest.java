package com.limelight.nvstream;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class QuitTrackerTest {
    @Test public void duplicateCancelIsCoalescedAndWaiterContinues() throws Exception {
        String host = "host-success-" + System.nanoTime();
        QuitTracker.Ticket ticket = QuitTracker.begin(host);
        assertNotNull(ticket);
        assertNull(QuitTracker.begin(host));
        assertTrue(QuitTracker.isPending(host));
        QuitTracker.finish(host, ticket, null);
        QuitTracker.await(host);
        assertFalse(QuitTracker.isPending(host));
    }

    @Test public void cleanupFailureReachesWaitingConnection() {
        String host = "host-failure-" + System.nanoTime();
        QuitTracker.Ticket ticket = QuitTracker.begin(host);
        QuitTracker.finish(host, ticket, "cleanup failed");
        try {
            QuitTracker.await(host);
            fail("expected failure");
        } catch (IOException e) {
            assertEquals("cleanup failed", e.getMessage());
        }
    }
}
