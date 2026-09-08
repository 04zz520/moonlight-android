package com.limelight.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import com.limelight.nvstream.LatestScaleQueue;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Sunshine controls. One worker, actual DPI feedback, last choice wins. */
public final class NativeScaleDialog {
    public interface Transport { JSONObject call(String device, Integer value) throws Exception; }
    private final Activity activity;
    private final Transport transport;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LatestScaleQueue queue = new LatestScaleQueue();
    private volatile boolean closed;
    private String device;
    private AlertDialog dialog;
    private TextView status;
    private LinearLayout buttons;
    private int revision;

    public NativeScaleDialog(Activity activity, Transport transport) {
        this.activity = activity; this.transport = transport;
    }
    private void ui(Runnable action) {
        activity.runOnUiThread(() -> { if (!closed && !activity.isFinishing() && !activity.isDestroyed()) action.run(); });
    }
    public void show() {
        if (closed || (dialog != null && dialog.isShowing())) return;
        final int viewRevision = ++revision;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(16 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(padding,padding,padding,padding);
        status = new TextView(activity);
        status.setText("正在向 Sunshine 查询实际缩放…");
        content.addView(status);
        buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.VERTICAL);
        content.addView(buttons);
        ScrollView scroll = new ScrollView(activity); scroll.addView(content);
        dialog = new AlertDialog.Builder(activity).setTitle("电脑缩放 · Sunshine 原生控制")
                .setView(scroll).setNegativeButton("关闭", null).create();
        dialog.show();
        worker.execute(() -> {
            try {
                JSONObject info = transport.call(null, null);
                String id = info.optString("device_id");
                // This setup streams the primary Zako virtual display; never fall back to a physical screen.
                String friendly = info.optString("friendly_name");
                if (!info.optBoolean("is_primary") || !friendly.toLowerCase(java.util.Locale.ROOT).contains("zako"))
                    throw new Exception("当前主屏不是 Zako 串流屏，已停止调节以保护本地桌面");
                if (id.isEmpty() || !info.optBoolean("scale_set_supported")) throw new Exception("目标屏不支持实时缩放");
                JSONArray options = info.getJSONArray("supported_scale_percents");
                ui(() -> {
                    if (viewRevision != revision) return;
                    device = id;
                    status.setText("实际缩放：" + info.optInt("current_scale_percent") + "%\n连续选择会保留最后一次；无需重连。");
                    for (int i=0; i<options.length(); i++) {
                        int value = options.optInt(i);
                        if (value < 100 || value > 500) continue;
                        Button button = new Button(activity); button.setText(value + "%");
                        button.setOnClickListener(v -> choose(value));
                        buttons.addView(button);
                    }
                });
            } catch (Exception e) { ui(() -> { if(viewRevision == revision) status.setText("无法读取：" + message(e)); }); }
        });
    }
    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
    private void choose(int value) {
        if (device == null || closed) return;
        status.setText("正在申请 " + value + "%…");
        if (queue.offer(value)) worker.execute(this::drain);
    }
    private void drain() {
        Integer requested;
        while (!closed && (requested = queue.next()) != null) {
            final int target = requested;
            final String targetDevice = device;
            try {
                // Query the pinned device before each write; never reuse a remembered DPI as actual state.
                JSONObject before = transport.call(targetDevice, null);
                if (!before.optBoolean("is_primary")) throw new Exception("目标屏已不是串流主屏，未修改缩放");
                JSONObject result = before.optInt("current_scale_percent") == target ? before : transport.call(targetDevice, target);
                int actual = result.optInt("current_scale_percent", -1);
                if (actual != target) {
                    actual = transport.call(targetDevice, null).optInt("current_scale_percent", -1);
                }
                final int confirmed = actual;
                ui(() -> status.setText(confirmed == target ? "已确认：电脑实际缩放 " + confirmed + "%" :
                        "请求 " + target + "% 尚未确认生效；实际 " + confirmed + "%（可重新查询）"));
            } catch (Exception e) {
                // A timed-out write may have applied. Do not automatically replay it.
                ui(() -> status.setText("未能确认调节结果：" + message(e) + "\n关闭后重新打开可查询实际值。"));
            }
        }
    }
    public void close() {
        closed = true; queue.close(); worker.shutdownNow();
        if (dialog != null) dialog.dismiss();
    }
}
