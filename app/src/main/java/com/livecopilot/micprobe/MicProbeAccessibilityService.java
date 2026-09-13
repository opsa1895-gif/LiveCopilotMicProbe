package com.livecopilot.micprobe;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class MicProbeAccessibilityService extends AccessibilityService {
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout overlay;
    private TextView statusText;
    private TextView appText;
    private TextView meterText;
    private Button startButton;
    private Button stopButton;

    private MicProbeEngine engine;
    private String foregroundPackage = "неизвестно";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        engine = new MicProbeEngine(this, this::renderSnapshot);
        showOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();
        if (!pkg.equals(getPackageName())) {
            foregroundPackage = pkg;
        }

        if (isTikTokPackage(pkg) && engine != null) {
            engine.markTikTokSeen();
        }

        if (appText != null) {
            appText.setText(isTikTokPackage(foregroundPackage)
                    ? "Foreground: TikTok ✓"
                    : "Foreground: " + foregroundPackage);
        }
    }

    @Override
    public void onInterrupt() {
        // Nothing to restore. The user controls the service from Android settings.
    }

    @Override
    public void onDestroy() {
        if (engine != null && engine.isRunning()) {
            engine.stop("service_destroyed");
        }
        removeOverlay();
        super.onDestroy();
    }

    private void showOverlay() {
        if (overlay != null) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(225, 20, 20, 24));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.argb(150, 255, 255, 255));
        overlay.setBackground(bg);

        TextView header = text("LIVE COPILOT • MIC PROBE", 15, Color.WHITE);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, dp(6));
        overlay.addView(header);

        appText = text("Foreground: неизвестно", 12, Color.LTGRAY);
        overlay.addView(appText);

        statusText = text("Готово. Натисни START, после отвори TikTok Live.", 14, Color.WHITE);
        statusText.setPadding(0, dp(8), 0, dp(6));
        overlay.addView(statusText);

        meterText = text("Mic: -- dBFS", 13, Color.LTGRAY);
        overlay.addView(meterText);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(10), 0, 0);

        startButton = button("START");
        stopButton = button("STOP");
        Button hideButton = button("×");

        startButton.setOnClickListener(v -> {
            if (engine != null) engine.start();
        });
        stopButton.setOnClickListener(v -> {
            if (engine != null) engine.stop("user_stopped");
        });
        hideButton.setOnClickListener(v -> {
            if (engine != null && engine.isRunning()) {
                engine.stop("overlay_hidden");
            }
            removeOverlay();
        });

        buttons.addView(startButton);
        buttons.addView(stopButton);
        buttons.addView(hideButton);
        overlay.addView(buttons);

        params = new WindowManager.LayoutParams(
                dp(350),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(70);

        installDrag(header);
        windowManager.addView(overlay, params);
    }

    private void removeOverlay() {
        if (windowManager != null && overlay != null) {
            try {
                windowManager.removeView(overlay);
            } catch (Throwable ignored) {
            }
        }
        overlay = null;
        statusText = null;
        appText = null;
        meterText = null;
        startButton = null;
        stopButton = null;
    }

    private void renderSnapshot(MicProbeEngine.Snapshot snapshot) {
        if (overlay == null) return;

        statusText.setText(snapshot.status);
        meterText.setText(String.format(Locale.US,
                "Mic: %.1f dBFS  •  silenced events: %d",
                snapshot.dbfs,
                snapshot.silenceEvents));

        if (snapshot.clientSilenced) {
            statusText.setTextColor(Color.rgb(255, 100, 100));
        } else if (snapshot.running) {
            statusText.setTextColor(Color.rgb(120, 255, 160));
        } else {
            statusText.setTextColor(Color.WHITE);
        }

        if (startButton != null) startButton.setEnabled(!snapshot.running);
        if (stopButton != null) stopButton.setEnabled(snapshot.running);
    }

    private void installDrag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downRawX;
            float downRawY;
            int downX;
            int downY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (params == null || windowManager == null || overlay == null) return false;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downX = params.x;
                        downY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = downX + Math.round(event.getRawX() - downRawX);
                        params.y = downY + Math.round(event.getRawY() - downRawY);
                        windowManager.updateViewLayout(overlay, params);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                dp(44)
        );
        lp.setMargins(dp(4), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static boolean isTikTokPackage(String pkg) {
        return "com.zhiliaoapp.musically".equals(pkg) ||
                "com.ss.android.ugc.trill".equals(pkg);
    }
}
