package com.limelight.ui;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;
import com.limelight.R;
import com.limelight.nvstream.LatestScaleQueue;
import com.limelight.ui.gamemenu.GameDisplayScaleFragment;
import com.limelight.utils.UiHelper;
import org.apmem.tools.layouts.FlowLayout;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Session-owned native scale controller; view uses the standard game-menu page. */
public final class NativeScaleDialog {
    public interface Transport { JSONObject call(String device, Integer value) throws Exception; }
    private final Activity activity;
    private final Transport transport;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LatestScaleQueue queue = new LatestScaleQueue();
    private volatile boolean closed;
    private volatile String device;
    private volatile int revision;
    private volatile int choiceRevision;
    private int confirmedScale = -1;
    private View root;
    private TextView status, actual;
    private FlowLayout buttons;
    private View refresh;
    private GameDisplayScaleFragment page;

    public NativeScaleDialog(Activity activity, Transport transport) {
        this.activity = activity; this.transport = transport;
    }
    private void ui(int version, Runnable action) {
        activity.runOnUiThread(() -> {
            if (!closed && root != null && version == revision &&
                    !activity.isFinishing() && !activity.isDestroyed()) action.run();
        });
    }
    public void show() {
        if (closed || page != null) return;
        page = new GameDisplayScaleFragment();
        page.setWidth(UiHelper.dpToPx(activity, 364));
        page.show(activity.getFragmentManager());
    }
    public void bind(View view) {
        root = view;
        status = view.findViewById(R.id.host_scale_status);
        actual = view.findViewById(R.id.host_scale_actual);
        buttons = view.findViewById(R.id.host_scale_options);
        refresh = view.findViewById(R.id.btn_right);
        refresh.setOnClickListener(v -> query());
        query();
    }
    public void unbind(View view) {
        if (root != view) return;
        ++revision;
        root = null; status = null; actual = null; buttons = null; refresh = null; page = null;
    }
    private static void validate(JSONObject info) throws Exception {
        if (!info.optBoolean("is_primary") ||
                !info.optString("friendly_name").toLowerCase(java.util.Locale.ROOT).contains("zako"))
            throw new Exception("当前主屏不是 Zako 串流屏，已停止调节以保护本地桌面");
        if (info.optString("device_id").isEmpty() || !info.optBoolean("scale_set_supported"))
            throw new Exception("目标屏不支持实时缩放");
    }
    private void query() {
        if (closed || root == null) return;
        final int version = ++revision;
        refresh.setEnabled(false);
        buttons.removeAllViews();
        actual.setText("—");
        status.setText(R.string.host_scale_loading);
        worker.execute(() -> {
            try {
                JSONObject info = transport.call(null, null);
                validate(info);
                JSONArray options = info.getJSONArray("supported_scale_percents");
                ui(version, () -> {
                    device = info.optString("device_id");
                    for (int i = 0; i < options.length(); i++) {
                        int value = options.optInt(i);
                        if (value < 100 || value > 500) continue;
                        ToggleButton button = new ToggleButton(activity);
                        button.setTextOn(value + "%"); button.setTextOff(value + "%");
                        button.setText(value + "%"); button.setTag(value);
                        button.setTextColor(Color.WHITE); button.setTextSize(12);
                        button.setGravity(Gravity.CENTER);
                        button.setBackgroundResource(R.drawable.ic_game_menu_btn_selector);
                        button.setBackgroundTintList(null);
                        FlowLayout.LayoutParams lp = new FlowLayout.LayoutParams(dp(72), dp(40));
                        lp.setMargins(0, dp(6), dp(6), 0);
                        buttons.addView(button, lp);
                        button.setOnClickListener(v -> {
                            // Only host confirmation selects a chip, never the local click itself.
                            displayActual(confirmedScale);
                            choose(value);
                        });
                    }
                    displayActual(info.optInt("current_scale_percent", -1));
                    status.setText("点击比例立即应用");
                    refresh.setEnabled(true);
                });
            } catch (Exception e) {
                ui(version, () -> {
                    status.setText("无法读取：" + message(e) + "\n请检查连接后点击刷新。");
                    refresh.setEnabled(true);
                });
            }
        });
    }
    private int dp(int value) { return UiHelper.dpToPx(activity, value); }
    private void displayActual(int value) {
        confirmedScale = value;
        actual.setText(value > 0 ? value + "%" : "—");
        for (int i = 0; i < buttons.getChildCount(); i++) {
            ToggleButton button = (ToggleButton) buttons.getChildAt(i);
            button.setChecked(((Integer)button.getTag()) == value);
        }
    }
    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
    private void choose(int value) {
        if (device == null || closed) return;
        ++choiceRevision;
        status.setText("正在应用 " + value + "%…");
        final int version = revision;
        final String targetDevice = device;
        if (queue.offer(value)) worker.execute(() -> drain(version, targetDevice));
    }
    private void drain(int version, String targetDevice) {
        Integer requested;
        while (!closed && (requested = queue.next()) != null) {
            final int target = requested;
            final int choice = choiceRevision;
            try {
                JSONObject before = transport.call(targetDevice, null);
                validate(before);
                if (!targetDevice.equals(before.optString("device_id")))
                    throw new Exception("串流显示器已改变，请刷新后重试");
                JSONObject result = before.optInt("current_scale_percent") == target ? before : transport.call(targetDevice, target);
                int value = result.optInt("current_scale_percent", -1);
                if (value != target) value = transport.call(targetDevice, null).optInt("current_scale_percent", -1);
                final int confirmed = value;
                ui(version, () -> {
                    displayActual(confirmed);
                    if (choice == choiceRevision)
                        status.setText(confirmed == target ? "已应用" : "尚未确认生效，请点击刷新核对。");
                });
            } catch (Exception e) {
                // A timed-out write may have applied. Never automatically replay it.
                ui(version, () -> {
                    if (choice == choiceRevision) {
                        displayActual(-1);
                        status.setText("未能确认：" + message(e) + "\n请点击刷新核对实际比例。");
                    }
                });
            }
        }
    }
    public void close() {
        closed = true; queue.close(); worker.shutdownNow();
        if (page != null) page.dismissAllowingStateLoss();
        root = null;
    }
}
