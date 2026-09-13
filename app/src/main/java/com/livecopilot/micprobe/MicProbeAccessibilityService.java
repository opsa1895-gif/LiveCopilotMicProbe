package com.livecopilot.micprobe;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Locale;

public class MicProbeAccessibilityService extends AccessibilityService implements MicProbeEngine.Listener {
    private static final long ANSWER_INTERVAL_MS = 4_000L;
    private static final long PARTIAL_COMMIT_MS = 1_600L;
    private static final int MAX_CONTEXT_ITEMS = 10;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout overlay;
    private TextView statusText, appText, meterText, transcriptText;
    private TextView directText, sarcasticText, funnyText, calmText;
    private Button startButton, stopButton;

    private MicProbeEngine engine;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private boolean recognitionWanted;
    private boolean restartingRecognition;
    private String foregroundPackage = "неизвестно";

    private final Deque<String> contextItems = new ArrayDeque<>();
    private String latestPartial = "";
    private String lastCommitted = "";
    private String lastAnsweredFocus = "";
    private long lastPartialCommitMs;
    private long lastAnswerMs;
    private boolean contextDirty;

    private final Runnable liveTicker = new Runnable() {
        @Override public void run() {
            if (!recognitionWanted || overlay == null) return;
            long now = SystemClock.elapsedRealtime();
            if (!latestPartial.isEmpty() && now - lastPartialCommitMs >= PARTIAL_COMMIT_MS) {
                commitSpeech(latestPartial, false);
                lastPartialCommitMs = now;
            }
            maybeGenerateAnswer();
            overlay.postDelayed(this, 500L);
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        engine = new MicProbeEngine(this, this);
        setupSpeechRecognizer();
        showOverlay();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (!pkg.equals(getPackageName())) foregroundPackage = pkg;
        if (isTikTokPackage(pkg) && engine != null) engine.markTikTokSeen();
        if (appText != null) appText.setText(isTikTokPackage(foregroundPackage) ? "TikTok активен ✓" : "Foreground: " + foregroundPackage);
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        stopRecognition();
        if (recognizer != null) { try { recognizer.destroy(); } catch (Throwable ignored) {} }
        if (engine != null && engine.isRunning()) engine.stop("service_destroyed");
        removeOverlay();
        super.onDestroy();
    }

    @Override public void onSnapshot(MicProbeEngine.Snapshot snapshot) { renderSnapshot(snapshot); }
    @Override public void onPcmChunk(short[] samples, int sampleRate) {}

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bg-BG");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 550L);

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { setAiStatus("Слушам активно…"); }
            @Override public void onBeginningOfSpeech() { setAiStatus("Чувам реч • обновявам контекста"); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { setAiStatus("Обработвам последната реплика…"); }

            @Override public void onError(int error) {
                if (!recognitionWanted) return;
                scheduleRecognitionRestart((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ? 200L : 650L);
            }

            @Override public void onResults(Bundle results) {
                String best = bestText(results);
                if (!best.isEmpty()) {
                    latestPartial = best;
                    commitSpeech(best, true);
                }
                if (recognitionWanted) scheduleRecognitionRestart(120L);
            }

            @Override public void onPartialResults(Bundle partialResults) {
                String best = bestText(partialResults);
                if (best.isEmpty()) return;
                latestPartial = best;
                if (transcriptText != null) transcriptText.setText("Чувам: " + shorten(best, 100));
                long now = SystemClock.elapsedRealtime();
                if (now - lastPartialCommitMs >= PARTIAL_COMMIT_MS && isMeaningfullyDifferent(best, lastCommitted)) {
                    commitSpeech(best, false);
                    lastPartialCommitMs = now;
                }
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private String bestText(Bundle bundle) {
        if (bundle == null) return "";
        ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty() || list.get(0) == null) return "";
        return list.get(0).replace('\n', ' ').trim();
    }

    private void commitSpeech(String text, boolean isFinal) {
        String clean = text == null ? "" : text.replace('\n', ' ').trim();
        if (clean.length() < 3) return;
        if (!isMeaningfullyDifferent(clean, lastCommitted)) return;

        if (!contextItems.isEmpty() && isEvolutionOf(contextItems.peekLast(), clean)) {
            contextItems.removeLast();
        }
        contextItems.addLast(clean);
        while (contextItems.size() > MAX_CONTEXT_ITEMS) contextItems.removeFirst();

        lastCommitted = clean;
        contextDirty = true;
        if (transcriptText != null) transcriptText.setText((isFinal ? "Последно: " : "В движение: ") + shorten(clean, 100));

        if (isFinal && SystemClock.elapsedRealtime() - lastAnswerMs >= 1_500L) {
            maybeGenerateAnswer();
        }
    }

    private boolean isEvolutionOf(String oldText, String newText) {
        if (oldText == null || newText == null) return false;
        String a = normalize(oldText), b = normalize(newText);
        if (a.isEmpty() || b.isEmpty()) return false;
        return b.startsWith(a) || a.startsWith(b) || overlapRatio(a, b) > 0.72;
    }

    private boolean isMeaningfullyDifferent(String a, String b) {
        String x = normalize(a), y = normalize(b);
        if (x.isEmpty()) return false;
        if (y.isEmpty()) return true;
        if (x.equals(y)) return false;
        if (x.length() > y.length() + 12 || y.length() > x.length() + 12) return true;
        return overlapRatio(x, y) < 0.82;
    }

    private double overlapRatio(String a, String b) {
        String[] aw = a.split("\\s+");
        String[] bw = b.split("\\s+");
        if (aw.length == 0 || bw.length == 0) return 0.0;
        int common = 0;
        for (String x : aw) {
            for (String y : bw) {
                if (x.equals(y)) { common++; break; }
            }
        }
        return common / (double)Math.max(aw.length, bw.length);
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
    }

    private void startRecognitionLoop() {
        recognitionWanted = true;
        contextItems.clear(); latestPartial = ""; lastCommitted = ""; lastAnsweredFocus = "";
        contextDirty = false; lastPartialCommitMs = 0L; lastAnswerMs = 0L;
        if (recognizer == null || recognizerIntent == null) { setAiStatus("Няма Android SpeechRecognizer на този телефон."); return; }
        try { recognizer.startListening(recognizerIntent); } catch (Throwable t) { setAiStatus("Speech recognition не стартира."); }
        if (overlay != null) { overlay.removeCallbacks(liveTicker); overlay.postDelayed(liveTicker, 500L); }
    }

    private void scheduleRecognitionRestart(long delayMs) {
        if (!recognitionWanted || restartingRecognition) return;
        restartingRecognition = true;
        if (overlay == null) return;
        overlay.postDelayed(() -> {
            restartingRecognition = false;
            if (!recognitionWanted || recognizer == null) return;
            try { recognizer.startListening(recognizerIntent); }
            catch (Throwable ignored) { scheduleRecognitionRestart(650L); }
        }, delayMs);
    }

    private void stopRecognition() {
        recognitionWanted = false; restartingRecognition = false;
        if (overlay != null) overlay.removeCallbacks(liveTicker);
        if (recognizer != null) { try { recognizer.cancel(); } catch (Throwable ignored) {} }
    }

    private void maybeGenerateAnswer() {
        long now = SystemClock.elapsedRealtime();
        if (!contextDirty || contextItems.isEmpty()) return;
        if (lastAnswerMs != 0L && now - lastAnswerMs < ANSWER_INTERVAL_MS) return;

        String focus = contextItems.peekLast();
        if (!isMeaningfullyDifferent(focus, lastAnsweredFocus) && contextItems.size() < 2) return;

        String context = buildContext();
        ReplyGenerator.Replies r = ReplyGenerator.generate(context, focus);
        if (directText != null) directText.setText("ТОЧЕН: " + r.direct);
        if (sarcasticText != null) sarcasticText.setText("САРКАЗЪМ: " + r.sarcastic);
        if (funnyText != null) funnyText.setText("ЗАБАВЕН: " + r.funny);
        if (calmText != null) calmText.setText("СПОКОЕН: " + r.calm);
        if (transcriptText != null) transcriptText.setText("Фокус: " + shorten(focus, 100));
        setAiStatus("Нови отговори ✓ • слушам нататък");

        lastAnsweredFocus = focus;
        lastAnswerMs = now;
        contextDirty = false;
    }

    private String buildContext() {
        StringBuilder sb = new StringBuilder();
        for (String item : contextItems) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(item);
        }
        return sb.toString();
    }

    private void setAiStatus(String value) { if (statusText != null) statusText.setText(value); }

    private void showOverlay() {
        if (overlay != null) return;
        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(230, 18, 18, 22)); bg.setCornerRadius(dp(16)); bg.setStroke(dp(1), Color.argb(140,255,255,255));
        overlay.setBackground(bg);

        TextView header = text("LIVE COPILOT • v0.4", 14, Color.WHITE);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        overlay.addView(header);
        appText = text("Foreground: неизвестно", 11, Color.LTGRAY); overlay.addView(appText);
        statusText = text("START → жив контекст + отговори при промяна", 12, Color.WHITE); statusText.setPadding(0,dp(6),0,dp(4)); overlay.addView(statusText);
        meterText = text("Mic: -- dBFS", 11, Color.LTGRAY); overlay.addView(meterText);
        transcriptText = text("Фокус: —", 12, Color.rgb(210,220,255)); transcriptText.setMaxLines(2); transcriptText.setPadding(0,dp(7),0,dp(6)); overlay.addView(transcriptText);

        directText = replyBox("ТОЧЕН: —"); sarcasticText = replyBox("САРКАЗЪМ: —"); funnyText = replyBox("ЗАБАВЕН: —"); calmText = replyBox("СПОКОЕН: —");
        overlay.addView(directText); overlay.addView(sarcasticText); overlay.addView(funnyText); overlay.addView(calmText);

        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL); buttons.setGravity(Gravity.END); buttons.setPadding(0,dp(8),0,0);
        startButton = button("START"); stopButton = button("STOP"); Button hideButton = button("×");
        startButton.setOnClickListener(v -> { if (engine != null) engine.start(); startRecognitionLoop(); });
        stopButton.setOnClickListener(v -> { stopRecognition(); if (engine != null) engine.stop("user_stopped"); });
        hideButton.setOnClickListener(v -> { stopRecognition(); if (engine != null && engine.isRunning()) engine.stop("overlay_hidden"); removeOverlay(); });
        buttons.addView(startButton); buttons.addView(stopButton); buttons.addView(hideButton); overlay.addView(buttons);

        params = new WindowManager.LayoutParams(dp(365), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START; params.x = dp(8); params.y = dp(48);
        installDrag(header); windowManager.addView(overlay, params);
    }

    private TextView replyBox(String initial) {
        TextView t = text(initial, 13, Color.WHITE); t.setMaxLines(2); t.setPadding(dp(8),dp(7),dp(8),dp(7));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.argb(110,255,255,255)); bg.setCornerRadius(dp(10)); t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(0,dp(3),0,0); t.setLayoutParams(lp); return t;
    }

    private void removeOverlay() {
        if (overlay != null) overlay.removeCallbacks(liveTicker);
        if (windowManager != null && overlay != null) { try { windowManager.removeView(overlay); } catch (Throwable ignored) {} }
        overlay = null; statusText = null; appText = null; meterText = null; transcriptText = null; directText = null; sarcasticText = null; funnyText = null; calmText = null; startButton = null; stopButton = null;
    }

    private void renderSnapshot(MicProbeEngine.Snapshot snapshot) {
        if (overlay == null) return;
        meterText.setText(String.format(Locale.US,"Mic %.1f dBFS • silenced %d",snapshot.dbfs,snapshot.silenceEvents));
        if (snapshot.clientSilenced) { statusText.setText("Mic client е заглушен от Android"); statusText.setTextColor(Color.rgb(255,100,100)); }
        else if (snapshot.running) statusText.setTextColor(Color.rgb(120,255,160));
        else { statusText.setText(snapshot.status); statusText.setTextColor(Color.WHITE); }
        if (startButton != null) startButton.setEnabled(!snapshot.running);
        if (stopButton != null) stopButton.setEnabled(snapshot.running);
    }

    private void installDrag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY; int downX, downY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (params == null || windowManager == null || overlay == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: downRawX=event.getRawX(); downRawY=event.getRawY(); downX=params.x; downY=params.y; return true;
                    case MotionEvent.ACTION_MOVE: params.x=downX+Math.round(event.getRawX()-downRawX); params.y=downY+Math.round(event.getRawY()-downRawY); windowManager.updateViewLayout(overlay,params); return true;
                    default: return false;
                }
            }
        });
    }

    private TextView text(String value, int sp, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); return t; }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,dp(42)); lp.setMargins(dp(4),0,0,0); b.setLayoutParams(lp); return b; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String shorten(String s, int max) { String clean=s==null?"":s.replace('\n',' ').trim(); return clean.length()<=max?clean:clean.substring(0,Math.max(1,max-1))+"…"; }
    private static boolean isTikTokPackage(String pkg) { return "com.zhiliaoapp.musically".equals(pkg) || "com.ss.android.ugc.trill".equals(pkg); }
}
