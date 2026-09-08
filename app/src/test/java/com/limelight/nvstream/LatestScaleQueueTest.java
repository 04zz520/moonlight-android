package com.limelight.nvstream;
import org.junit.Test;
import static org.junit.Assert.*;
public class LatestScaleQueueTest {
    @Test public void keepsLastWhileBusy() {
        LatestScaleQueue q = new LatestScaleQueue();
        assertTrue(q.offer(150)); assertEquals(Integer.valueOf(150), q.next());
        assertFalse(q.offer(200)); assertFalse(q.offer(225));
        assertEquals(Integer.valueOf(225), q.next()); assertNull(q.next());
        assertTrue(q.offer(100)); assertEquals(Integer.valueOf(100), q.next());
    }
    @Test public void closeDropsPending() {
        LatestScaleQueue q = new LatestScaleQueue(); q.offer(175); q.close();
        assertNull(q.next()); assertFalse(q.offer(200));
    }
}
