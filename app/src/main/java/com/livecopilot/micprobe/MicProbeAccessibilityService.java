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

public class MicProbeAccessibilityService extends AccessibilityService implements MicProbeEngine.Listener, OpenAiCopilotClient.Listener {
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout overlay;
    private TextView statusText;
    private TextView appText;
    private TextView meterText;
    private TextView transcriptText;
    private TextView directText;
    private TextView sarcasticText;
    private TextView funnyText;
    private TextView calmText;
    private Button startButton;
    private Button stopButton;

    private MicProbeEngine engine;
    private OpenAiCopilotClient aiClient;
    private String foregroundPackage = "неизвестно";
    private String aiStatus = "Натисни START";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        engine = new MicProbeEngine(this, this);
        aiClient = new OpenAiCopilotClient(this, this);
        showOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (!pkg.equals(getPackageName())) foregroundPackage = pkg;
        if (isTikTokPackage(pkg) && engine != null) engine.markTikTokSeen();
        if (appText != null) {
            appText.setText(isTikTokPackage(foregroundPackage)
                    ? "TikTok активен ✓"
                    : "Foreground: " + foregroundPackage);
        }
    }

    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (engine != null && engine.isRunning()) engine.stop("service_destroyed");
        if (aiClient != null) aiClient.shutdown();
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public void onSnapshot(MicProbeEngine.Snapshot snapshot) {
        renderSnapshot(snapshot);
    }

    @Override
    public void onPcmChunk(short[] samples, int sampleRate) {
        if (aiClient != null) aiClient.submitAudio(samples, sampleRate);
    }

    @Override
    public void onTranscript(String transcript) {
        getMainExecutor().execute(() -> {
            if (transcriptText != null) transcriptText.setText("Чух: " + shorten(transcript, 120));
        });
    }

    @Override
    public void onReplies(OpenAiCopilotClient.Replies replies) {
        getMainExecutor().execute(() -> {
            if (directText != null) directText.setText("ТОЧЕН: " + replies.direct);
            if (sarcasticText != null) sarcasticText.setText("САРКАЗЪМ: " + replies.sarcastic);
            if (funnyText != null) funnyText.setText("ЗАБАВЕН: " + replies.funny);
            if (calmText != null) calmText.setText("СПОКОЕН: " + replies.calm);
        });
    }

    @Override
    public void onStatus(String status) {
        aiStatus = status;
        getMainExecutor().execute(() -> {
            if (statusText != null) statusText.setText(status);
        });
    }

    private void showOverlay() {
        if (overlay != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(232, 18, 18, 22));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.argb(140, 255, 255, 255));
        overlay.setBackground(bg);

        TextView header = text("LIVE COPILOT • v0.5 CLOUD STT", 14, Color.WHITE);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        overlay.addView(header);

        appText = text("Foreground: неизвестно", 11, Color.LTGRAY);
        overlay.addView(appText);

        statusText = text("START → по-точно cloud разпознаване", 12, Color.WHITE);
        statusText.setPadding(0, dp(6), 0, dp(4));
        overlay.addView(statusText);

        meterText = text("Mic: -- dBFS", 11, Color.LTGRAY);
        overlay.addView(meterText);

        transcriptText = text("Чух: —", 12, Color.rgb(210, 220, 255));
        transcriptText.setMaxLines(3);
        transcriptText.setPadding(0, dp(7), 0, dp(6));
        overlay.addView(transcriptText);

        directText = replyBox("ТОЧЕН: —");
        sarcasticText = replyBox("САРКАЗЪМ: —");
        funnyText = replyBox("ЗАБАВЕН: —");
        calmText = replyBox("СПОКОЕН: —");
        overlay.addView(directText);
        overlay.addView(sarcasticText);
        overlay.addView(funnyText);
        overlay.addView(calmText);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(8), 0, 0);

        startButton = button("START");
        stopButton = button("STOP");
        Button hideButton = button("×");

        startButton.setOnClickListener(v -> {
            aiStatus = "Слушам • изпращам кратки аудио сегменти за разпознаване";
            if (statusText != null) statusText.setText(aiStatus);
            if (engine != null) engine.start();
        });
        stopButton.setOnClickListener(v -> {
            if (engine != null) engine.stop("user_stopped");
            aiStatus = "Спряно";
        });
        hideButton.setOnClickListener(v -> {
            if (engine != null && engine.isRunning()) engine.stop("overlay_hidden");
            removeOverlay();
        });

        buttons.addView(startButton);
        buttons.addView(stopButton);
        buttons.addView(hideButton);
        overlay.addView(buttons);

        params = new WindowManager.LayoutParams(
                dp(370),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(8);
        params.y = dp(48);

        installDrag(header);
        windowManager.addView(overlay, params);
    }

    private TextView replyBox(String initial) {
        TextView t = text(initial, 13, Color.WHITE);
        t.setMaxLines(3);
        t.setPadding(dp(8), dp(7), dp(8), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(110, 255, 255, 255));
        bg.setCornerRadius(dp(10));
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, 0);
        t.setLayoutParams(lp);
        return t;
    }

    private void renderSnapshot(MicProbeEngine.Snapshot snapshot) {
        if (overlay == null) return;
        meterText.setText(String.format(Locale.US,
                "Mic %.1f dBFS • silenced %d",
                snapshot.dbfs, snapshot.silenceEvents));

        if (snapshot.clientSilenced) {
            statusText.setText("Mic client е заглушен от Android");
            statusText.setTextColor(Color.rgb(255, 100, 100));
        } else if (snapshot.running) {
            statusText.setText(aiStatus);
            statusText.setTextColor(Color.rgb(120, 255, 160));
        } else {
            statusText.setText(snapshot.status);
            statusText.setTextColor(Color.WHITE);
        }

        if (startButton != null) startButton.setEnabled(!snapshot.running);
        if (stopButton != null) stopButton.setEnabled(snapshot.running);
    }

    private void removeOverlay() {
        if (windowManager != null && overlay != null) {
            try { windowManager.removeView(overlay); } catch (Throwable ignored) {}
        }
        overlay = null;
        statusText = null;
        appText = null;
        meterText = null;
        transcriptText = null;
        directText = null;
        sarcasticText = null;
        funnyText = null;
        calmText = null;
        startButton = null;
        stopButton = null;
    }

    private void installDrag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY;
            int downX, downY;

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
                WindowManager.LayoutParams.WRAP_CONTENT, dp(42));
        lp.setMargins(dp(4), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(1, max - 1)) + "…";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static boolean isTikTokPackage(String pkg) {
        return "com.zhiliaoapp.musically".equals(pkg) ||
                "com.ss.android.ugc.trill".equals(pkg);
    }
}
