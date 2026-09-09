package com.limelight.nvstream.http;

import java.io.IOException;

/** Bounded, read-only confirmation after an accepted cancel. Clock injected for tests. */
public final class QuitConfirmation {
    public interface Probe { boolean isIdle(long remainingMs) throws IOException; }
    public interface Clock { long now(); void sleep(long ms) throws InterruptedException; }
    public static void await(Probe probe, Clock clock, long budgetMs) throws IOException {
        long deadline = clock.now() + budgetMs;
        IOException lastError = null;
        boolean sawBusy = false;
        while (clock.now() < deadline) {
            try {
                if (probe.isIdle(deadline - clock.now())) return;
                sawBusy = true;
                lastError = null;
            } catch (HostHttpResponseException e) {
                throw e; // Rejection/authentication errors are not asynchronous cleanup.
            } catch (IOException e) {
                lastError = e; // A brief display/network transition may recover.
            }
            try { clock.sleep(Math.min(500, Math.max(0, deadline - clock.now()))); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("已取消退出状态检查", e);
            }
        }
        throw new IOException(lastError != null ? "退出请求已发送，但无法确认主机状态，请检查网络或主机。"
                : sawBusy ? "主机尚未确认退出完成，请稍后重试或在电脑上检查。"
                : "退出状态检查超时。", lastError);
    }
    private QuitConfirmation() {}
}
