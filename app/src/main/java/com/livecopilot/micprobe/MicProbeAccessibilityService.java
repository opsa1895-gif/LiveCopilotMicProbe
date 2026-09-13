package com.livecopilot.micprobe;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public class MicProbeAccessibilityService extends AccessibilityService implements MicProbeEngine.Listener, OpenAiCopilotClient.Listener {
    private static final String UI_PREFS = "live_copilot_ui";
    private static final long CONTEXT_RESET_AFTER_PAUSE_MS = 90_000L;
    private static final long SEMANTIC_MIN_GAP_MS = 6_000L;
    private static final long SEMANTIC_FOCUS_MAX_AGE_MS = 12_000L;
    private static final int SEMANTIC_CONTEXT_TURNS = 6;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout overlay;
    private LinearLayout controls;
    private TextView headerText;
    private TextView statusText;
    private TextView answerText;
    private TextView debugText;
    private Button styleButton;
    private Button powerButton;
    private Button collapseButton;

    private MicProbeEngine engine;
    private OpenAiCopilotClient aiClient;
    private SemanticReplyFallback semanticFallback;
    private OpenAiCopilotClient.Replies currentReplies;
    private MicProbeEngine.Snapshot latestSnapshot;
    private final Deque<String> semanticTurns = new ArrayDeque<>();

    private String foregroundPackage = "неизвестно";
    private String lastTranscript = "";
    private String aiStatus = "Готов";
    private String pendingSemanticFocus = "";
    private int styleIndex;
    private boolean collapsed;
    private boolean debugVisible;
    private boolean hasStartedSession;
    private long answerUpdatedAtMs;
    private long pausedAtMs;
    private long pendingSemanticAtMs;
    private long lastSemanticRequestAtMs;
    private long semanticAnswerBaselineMs;
    private long activeSemanticRequestId = -1L;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        loadUiState();
        engine = new MicProbeEngine(this, this);
        aiClient = new OpenAiCopilotClient(this, this);
        semanticFallback = new SemanticReplyFallback(this, (requestId, replies) ->
                getMainExecutor().execute(() -> applySemanticReply(requestId, replies)));
        showOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (!pkg.equals(getPackageName())) foregroundPackage = pkg;
        if (isTikTokPackage(pkg) && engine != null) engine.markTikTokSeen();
        renderDebug();
    }

    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (engine != null && engine.isRunning()) engine.stop("service_destroyed");
        if (aiClient != null) aiClient.shutdown();
        if (semanticFallback != null) semanticFallback.shutdown();
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public void onSnapshot(MicProbeEngine.Snapshot snapshot) {
        latestSnapshot = snapshot;
        if (overlay == null) return;
        if (snapshot.clientSilenced) {
            aiStatus = "Микрофонът е блокиран";
            if (statusText != null) statusText.setTextColor(Color.rgb(255, 115, 115));
        } else if (!snapshot.running) {
            if (!"user_paused".equals(snapshot.status)) aiStatus = snapshot.status;
            if (statusText != null) statusText.setTextColor(Color.LTGRAY);
        } else if (statusText != null) {
            statusText.setTextColor(Color.rgb(145, 255, 175));
        }
        renderStatus();
        renderDebug();
        if (powerButton != null) powerButton.setText(snapshot.running ? "ПАУЗА" : "START");
    }

    @Override
    public void onVoiceActivity(boolean speaking) {
        if (overlay == null || engine == null || !engine.isRunning()) return;
        if (speaking) {
            aiStatus = "Слушам…";
            if (answerText != null && System.currentTimeMillis() - answerUpdatedAtMs > 12_000L) {
                answerText.setAlpha(0.55f);
            }
        } else {
            aiStatus = "Обработвам…";
        }
        renderStatus();
    }

    @Override
    public void onPcmChunk(short[] samples, int sampleRate) {
        if (aiClient != null && engine != null && engine.isRunning()) {
            short[] prepared = AudioPreprocessor.prepare(samples, sampleRate);
            if (prepared.length > 0) aiClient.submitAudio(prepared, sampleRate);
        }
    }

    @Override
    public void onTranscript(String transcript) {
        String clean = transcript == null ? "" : transcript.replace('\n', ' ').trim();
        lastTranscript = clean;
        if (!clean.isEmpty()) {
            semanticTurns.addLast(clean);
            while (semanticTurns.size() > SEMANTIC_CONTEXT_TURNS) semanticTurns.removeFirst();
            pendingSemanticFocus = clean;
            pendingSemanticAtMs = System.currentTimeMillis();
        }
        getMainExecutor().execute(this::renderDebug);
    }

    @Override
    public void onReplies(OpenAiCopilotClient.Replies replies) {
        getMainExecutor().execute(() -> {
            if (engine == null || !engine.isRunning() || replies == null) return;
            if (semanticFallback != null) semanticFallback.invalidate();
            activeSemanticRequestId = -1L;
            pendingSemanticFocus = "";
            currentReplies = mergeReplies(currentReplies, replies);
            answerUpdatedAtMs = System.currentTimeMillis();
            if (answerText != null) answerText.setAlpha(1f);
            if (collapsed && headerText != null) headerText.setText("AI •");
            renderSelectedReply();
        });
    }

    @Override
    public void onStatus(String status) {
        String raw = status == null ? "" : status.trim();
        aiStatus = simplifyStatus(raw);
        getMainExecutor().execute(() -> {
            renderStatus();
            if (isListeningStatus(raw)) scheduleSemanticFallbackIfNeeded();
        });
    }

    private void scheduleSemanticFallbackIfNeeded() {
        if (overlay == null || engine == null || !engine.isRunning()) return;
        if (pendingSemanticFocus.isEmpty() || semanticFallback == null) return;
        if (answerUpdatedAtMs >= pendingSemanticAtMs) {
            pendingSemanticFocus = "";
            return;
        }

        long now = System.currentTimeMillis();
        if (now - pendingSemanticAtMs > SEMANTIC_FOCUS_MAX_AGE_MS) {
            pendingSemanticFocus = "";
            return;
        }

        long wait = Math.max(0L, SEMANTIC_MIN_GAP_MS - (now - lastSemanticRequestAtMs));
        String focusSnapshot = pendingSemanticFocus;
        long focusTimeSnapshot = pendingSemanticAtMs;
        if (wait > 0L) {
            overlay.postDelayed(() -> {
                if (focusSnapshot.equals(pendingSemanticFocus)
                        && focusTimeSnapshot == pendingSemanticAtMs
                        && answerUpdatedAtMs < focusTimeSnapshot) {
                    requestSemanticFallback(focusSnapshot, focusTimeSnapshot);
                }
            }, wait);
            return;
        }
        requestSemanticFallback(focusSnapshot, focusTimeSnapshot);
    }

    private void requestSemanticFallback(String focus, long focusAtMs) {
        if (engine == null || !engine.isRunning() || semanticFallback == null) return;
        long now = System.currentTimeMillis();
        if (focus == null || focus.isEmpty() || now - focusAtMs > SEMANTIC_FOCUS_MAX_AGE_MS) return;
        if (answerUpdatedAtMs >= focusAtMs) return;
        if (now - lastSemanticRequestAtMs < SEMANTIC_MIN_GAP_MS) return;

        lastSemanticRequestAtMs = now;
        semanticAnswerBaselineMs = answerUpdatedAtMs;
        activeSemanticRequestId = semanticFallback.request(buildSemanticContext(), focus);
        pendingSemanticFocus = "";
    }

    private void applySemanticReply(long requestId, OpenAiCopilotClient.Replies replies) {
        if (requestId < 0L || requestId != activeSemanticRequestId) return;
        if (engine == null || !engine.isRunning() || replies == null) return;
        if (answerUpdatedAtMs > semanticAnswerBaselineMs) return;

        currentReplies = replies;
        answerUpdatedAtMs = System.currentTimeMillis();
        if (answerText != null) answerText.setAlpha(1f);
        if (collapsed && headerText != null) headerText.setText("AI •");
        renderSelectedReply();
        activeSemanticRequestId = -1L;
    }

    private String buildSemanticContext() {
        StringBuilder out = new StringBuilder();
        for (String turn : semanticTurns) {
            if (out.length() > 0) out.append('\n');
            out.append("- ").append(turn);
        }
        return out.toString();
    }

    private static OpenAiCopilotClient.Replies mergeReplies(
            OpenAiCopilotClient.Replies oldReplies,
            OpenAiCopilotClient.Replies incoming) {
        if (oldReplies == null) return incoming;
        String direct = nonEmpty(incoming.direct, oldReplies.direct);
        String sarcastic = nonEmpty(incoming.sarcastic, oldReplies.sarcastic);
        String funny = nonEmpty(incoming.funny, oldReplies.funny);
        String calm = nonEmpty(incoming.calm, oldReplies.calm);
        return new OpenAiCopilotClient.Replies(direct, sarcastic, funny, calm);
    }

    private static String nonEmpty(String preferred, String fallback) {
        return preferred == null || preferred.trim().isEmpty() ? fallback : preferred;
    }

    private static boolean isListeningStatus(String status) {
        String lower = status == null ? "" : status.toLowerCase(Locale.ROOT).trim();
        return lower.equals("слушам") || lower.equals("слушам…") || lower.contains("продължавам да слушам");
    }

    private void loadUiState() {
        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        styleIndex = Math.max(0, Math.min(3, prefs.getInt("style_index", 0)));
        collapsed = prefs.getBoolean("collapsed", false);
    }

    private void showOverlay() {
        if (overlay != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(10), dp(8), dp(10), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(224, 16, 16, 20));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.argb(95, 255, 255, 255));
        overlay.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        headerText = text("LIVE COPILOT", 13, Color.WHITE);
        headerText.setTypeface(headerText.getTypeface(), android.graphics.Typeface.BOLD);
        headerText.setSingleLine(true);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        headerText.setGravity(Gravity.CENTER_VERTICAL);
        headerText.setLayoutParams(headerLp);
        top.addView(headerText);

        collapseButton = smallButton("—");
        collapseButton.setOnClickListener(v -> setCollapsed(!collapsed));
        collapseButton.setOnLongClickListener(v -> {
            if (!collapsed) {
                debugVisible = !debugVisible;
                if (debugText != null) debugText.setVisibility(debugVisible ? View.VISIBLE : View.GONE);
                renderDebug();
            }
            return true;
        });
        top.addView(collapseButton);
        overlay.addView(top);

        statusText = text("Готов", 11, Color.LTGRAY);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        statusText.setPadding(0, dp(1), 0, dp(5));
        overlay.addView(statusText);

        answerText = text("Натисни START и отвори TikTok.", 16, Color.WHITE);
        answerText.setMaxLines(3);
        answerText.setEllipsize(TextUtils.TruncateAt.END);
        answerText.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable answerBg = new GradientDrawable();
        answerBg.setColor(Color.argb(95, 255, 255, 255));
        answerBg.setCornerRadius(dp(12));
        answerText.setBackground(answerBg);
        overlay.addView(answerText);

        debugText = text("", 10, Color.LTGRAY);
        debugText.setMaxLines(4);
        debugText.setEllipsize(TextUtils.TruncateAt.END);
        debugText.setPadding(0, dp(6), 0, 0);
        debugText.setVisibility(View.GONE);
        overlay.addView(debugText);

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.END);
        controls.setPadding(0, dp(7), 0, 0);

        styleButton = compactButton(styleLabel(styleIndex) + " ›");
        styleButton.setOnClickListener(v -> {
            styleIndex = (styleIndex + 1) % 4;
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putInt("style_index", styleIndex).apply();
            renderSelectedReply();
        });
        controls.addView(styleButton);

        powerButton = compactButton("START");
        powerButton.setOnClickListener(v -> toggleRunning());
        controls.addView(powerButton);
        overlay.addView(controls);

        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        int width = dp(collapsed ? 92 : 330);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int savedX = prefs.getInt("overlay_x", dp(8));
        int savedY = prefs.getInt("overlay_y", dp(50));

        params = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = clampInt(savedX, 0, Math.max(0, screenWidth - width));
        params.y = clampInt(savedY, 0, Math.max(0, screenHeight - dp(80)));

        applyCollapsedVisuals();
        installDrag(headerText);
        windowManager.addView(overlay, params);
    }

    private void toggleRunning() {
        if (engine == null) return;
        if (engine.isRunning()) {
            engine.stop("user_paused");
            pausedAtMs = System.currentTimeMillis();
            if (semanticFallback != null) semanticFallback.invalidate();
            activeSemanticRequestId = -1L;
            pendingSemanticFocus = "";
            aiStatus = "Пауза";
            renderStatus();
            return;
        }

        long now = System.currentTimeMillis();
        boolean resetContext = !hasStartedSession || pausedAtMs == 0L || now - pausedAtMs >= CONTEXT_RESET_AFTER_PAUSE_MS;
        if (resetContext && aiClient != null) aiClient.resetSession();
        if (resetContext && semanticFallback != null) semanticFallback.invalidate();
        hasStartedSession = true;

        if (resetContext) {
            currentReplies = null;
            answerUpdatedAtMs = 0L;
            lastTranscript = "";
            pendingSemanticFocus = "";
            pendingSemanticAtMs = 0L;
            semanticTurns.clear();
            if (answerText != null) {
                answerText.setText("Слушам разговора…");
                answerText.setAlpha(0.75f);
            }
        } else if (answerText != null) {
            answerText.setAlpha(0.65f);
        }

        aiStatus = "Слушам…";
        renderStatus();
        engine.start();
    }

    private void renderSelectedReply() {
        if (styleButton != null) styleButton.setText(styleLabel(styleIndex) + " ›");
        if (answerText == null || currentReplies == null) return;

        String value = selectedReply(currentReplies, styleIndex);
        if (value == null || value.trim().isEmpty()) value = currentReplies.direct;
        if (value == null || value.trim().isEmpty()) return;
        answerText.setText(value.trim());
        answerText.setAlpha(1f);
    }

    private static String selectedReply(OpenAiCopilotClient.Replies replies, int index) {
        switch (index) {
            case 1: return replies.sarcastic;
            case 2: return replies.funny;
            case 3: return replies.calm;
            default: return replies.direct;
        }
    }

    private String styleLabel(int index) {
        switch (index) {
            case 1: return "САРКАЗЪМ";
            case 2: return "ЗАБАВЕН";
            case 3: return "СПОКОЕН";
            default: return "ТОЧЕН";
        }
    }

    private void renderStatus() {
        if (statusText != null) statusText.setText(aiStatus == null || aiStatus.isEmpty() ? "Слушам" : aiStatus);
    }

    private void renderDebug() {
        if (debugText == null || !debugVisible) return;
        String app = isTikTokPackage(foregroundPackage) ? "TikTok ✓" : foregroundPackage;
        String mic = latestSnapshot == null
                ? "mic —"
                : String.format(Locale.US, "mic %.0f dB%s", latestSnapshot.dbfs,
                latestSnapshot.clientSilenced ? " • BLOCKED" : "");
        String heard = lastTranscript.isEmpty() ? "" : "\nЧух: " + shorten(lastTranscript, 115);
        debugText.setText(app + " • " + mic + heard);
    }

    private void setCollapsed(boolean value) {
        collapsed = value;
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putBoolean("collapsed", value).apply();
        applyCollapsedVisuals();
        if (params != null && windowManager != null && overlay != null) {
            params.width = dp(value ? 92 : 330);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            params.x = clampInt(params.x, 0, Math.max(0, screenWidth - params.width));
            try { windowManager.updateViewLayout(overlay, params); } catch (Throwable ignored) {}
            saveOverlayPosition();
        }
    }

    private void applyCollapsedVisuals() {
        if (statusText != null) statusText.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        if (answerText != null) answerText.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        if (controls != null) controls.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        if (debugText != null) debugText.setVisibility(!collapsed && debugVisible ? View.VISIBLE : View.GONE);
        if (headerText != null) headerText.setText(collapsed ? "AI" : "LIVE COPILOT");
        if (collapseButton != null) collapseButton.setText(collapsed ? "+" : "—");
    }

    private String simplifyStatus(String value) {
        if (value == null) return "Слушам";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("няма api") || (lower.contains("липсва") && lower.contains("api"))) return "Няма API key";
        if (lower.contains("разпознав")) return "Разпознавам…";
        if (lower.contains("мисля") || lower.contains("генерирам") || lower.contains("контекст")) return "Мисля…";
        if (lower.contains("грешка") || lower.contains("error") || lower.contains("прекъсна")) return "Проблем с AI връзката";
        if (lower.contains("готово") || lower.contains("продължавам") || lower.equals("слушам…") || lower.equals("слушам")) return "Слушам";
        return shorten(value, 38);
    }

    private void removeOverlay() {
        saveOverlayPosition();
        if (windowManager != null && overlay != null) {
            try { windowManager.removeView(overlay); } catch (Throwable ignored) {}
        }
        overlay = null;
        controls = null;
        headerText = null;
        statusText = null;
        answerText = null;
        debugText = null;
        styleButton = null;
        powerButton = null;
        collapseButton = null;
    }

    private void saveOverlayPosition() {
        if (params == null) return;
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                .putInt("overlay_x", params.x)
                .putInt("overlay_y", params.y)
                .apply();
    }

    private void installDrag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY;
            int downX, downY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (params == null || windowManager == null || overlay == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downX = params.x;
                        downY = params.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (Math.abs(dx) + Math.abs(dy) > dp(4)) moved = true;
                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        int screenHeight = getResources().getDisplayMetrics().heightPixels;
                        params.x = clampInt(downX + Math.round(dx), 0, Math.max(0, screenWidth - params.width));
                        params.y = clampInt(downY + Math.round(dy), 0, Math.max(0, screenHeight - dp(80)));
                        windowManager.updateViewLayout(overlay, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) saveOverlayPosition();
                        else if (collapsed) setCollapsed(false);
                        else v.performClick();
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

    private Button compactButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(11);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        lp.setMargins(dp(3), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(36));
        b.setLayoutParams(lp);
        return b;
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static boolean isTikTokPackage(String pkg) {
        return "com.zhiliaoapp.musically".equals(pkg) ||
                "com.ss.android.ugc.trill".equals(pkg);
    }
}
