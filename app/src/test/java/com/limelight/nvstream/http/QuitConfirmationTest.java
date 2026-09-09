package com.limelight.nvstream.http;

import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class QuitConfirmationTest {
    private static final class FakeClock implements QuitConfirmation.Clock {
        long time;
        public long now() { return time; }
        public void sleep(long ms) { time += ms; }
    }

    @Test public void busyThenIdleCompletesWithinBudget() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        QuitConfirmation.await(remaining -> calls.incrementAndGet() >= 4,
                new FakeClock(), 15000);
        assertEquals(4, calls.get());
    }

    @Test public void transientIoFailureCanRecover() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        QuitConfirmation.await(remaining -> {
            if (calls.incrementAndGet() == 1) throw new IOException("display switching");
            return true;
        }, new FakeClock(), 15000);
        assertEquals(2, calls.get());
    }

    @Test public void busyUntilDeadlineReportsCleanupTimeout() {
        try {
            QuitConfirmation.await(remaining -> false, new FakeClock(), 1200);
            fail("expected timeout");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("尚未确认"));
        }
    }

    @Test public void hostRejectionIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        try {
            QuitConfirmation.await(remaining -> {
                calls.incrementAndGet();
                throw new HostHttpResponseException(401, "denied");
            }, new FakeClock(), 15000);
            fail("expected rejection");
        } catch (IOException e) {
            assertTrue(e instanceof HostHttpResponseException);
            assertEquals(1, calls.get());
        }
    }
}
