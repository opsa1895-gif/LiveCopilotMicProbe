from pathlib import Path
import re


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected one {label}, found {count}")
    return text.replace(old, new, 1)


def replace_between(text, start, end, replacement, label):
    a = text.find(start)
    if a < 0:
        raise SystemExit(f"missing start for {label}")
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f"missing end for {label}")
    return text[:a] + replacement + text[b:]


client_path = Path("app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java")
client = client_path.read_text()
client = replace_once(
    client,
    "        void onStatus(String status);",
    "        void onStatus(long sessionSerial, long workSerial, boolean replyWork, String status);",
    "listener status signature",
)

process_audio = '''    private void processAudio(AudioItem item) {
        String key = SecretStore.loadApiKey(context);
        if (key.isEmpty()) {
            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Няма API key");
            return;
        }

        try {
            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Разпознавам…");
            String raw = transcribe(key, item.samples, item.sampleRate);
            if (!isCurrentSession(item.sessionSerial)) return;
            if (TranscriptQualityPolicy.isLowQuality(raw)) {
                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
                return;
            }

            long now = System.currentTimeMillis();
            String useful = removeRecentSelfEcho(raw, now);
            if (useful.isEmpty()) {
                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
                return;
            }

            prepareContextFor(useful, now);
            String focus = commitTranscript(useful, now);
            if (focus.isEmpty()) {
                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
                return;
            }

            // Keep stale speech in internal context, but never surface it over newer
            // live input or after it has become too old to be useful on screen.
            if (!shouldSurfaceAudioResult(item)) {
                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
                return;
            }

            listener.onTranscript(item.sessionSerial, item.inputSerial, focus);
            synchronized (this) {
                if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;
            }

            boolean actionable = isActionable(focus);
            long reference;
            synchronized (this) {
                reference = lastReplyAtMs > 0L ? lastReplyAtMs : firstSpeechAtMs;
            }
            boolean engagement = !actionable && reference > 0L && now - reference >= ENGAGEMENT_GAP_MS;

            if (actionable || engagement) {
                if (!queueReplyIfFresh(item, focus, engagement)) {
                    listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
                }
            } else {
                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
            }
        } catch (Throwable ignored) {
            if (isCurrentSession(item.sessionSerial)) {
                listener.onStatus(item.sessionSerial, item.inputSerial, false, "AI връзката прекъсна");
            }
        }
    }
'''
client = replace_between(
    client,
    "    private void processAudio(AudioItem item) {",
    "\n    private synchronized void prepareContextFor",
    process_audio,
    "processAudio",
)

process_reply = '''    private void processReply(ReplyJob job) {
        String key = SecretStore.loadApiKey(context);
        if (key.isEmpty()) return;
        String primary = "";

        try {
            listener.onStatus(job.sessionSerial, job.serial, true, "Мисля…");
            primary = primaryReply(key, job.context, job.focus, job.previousSuggestion, job.engagement, false);
            if (!current(job)) return;
            if (primary.isEmpty()) throw new IllegalStateException("empty primary");

            boolean varyDirect = !job.previousSuggestion.isEmpty()
                    && sameishReply(primary, job.previousSuggestion);

            synchronized (this) {
                lastDirectReply = primary;
                lastSarcasticReply = "";
                lastFunnyReply = "";
                lastCalmReply = "";
                lastReplyAtMs = System.currentTimeMillis();
            }
            listener.onReplies(job.sessionSerial, job.serial, new Replies(primary, "", "", ""));
            listener.onStatus(job.sessionSerial, job.serial, true, "Слушам");

            String primaryCopy = primary;
            variantExecutor.execute(() -> processVariants(job, key, primaryCopy, varyDirect));
        } catch (Throwable ignored) {
            if (!current(job)) return;
            ReplyGenerator.Replies local = ReplyGenerator.generate(job.context, job.focus);
            String direct = primary.isEmpty() ? local.direct : primary;
            synchronized (this) {
                lastDirectReply = direct;
                lastSarcasticReply = local.sarcastic;
                lastFunnyReply = local.funny;
                lastCalmReply = local.calm;
                lastReplyAtMs = System.currentTimeMillis();
            }
            listener.onReplies(job.sessionSerial, job.serial, new Replies(direct, local.sarcastic, local.funny, local.calm));
            listener.onStatus(job.sessionSerial, job.serial, true, "Слушам");
        }
    }
'''
client = replace_between(
    client,
    "    private void processReply(ReplyJob job) {",
    "\n    private void processVariants",
    process_reply,
    "processReply",
)

if 'listener.onStatus("' in client:
    raise SystemExit("untagged status callback remains")
client_path.write_text(client)


service_path = Path("app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java")
service = service_path.read_text()
service = replace_once(
    service,
    "    private long activeSemanticRequestId = -1L;",
    "    private long activeSemanticRequestId = -1L;\n    private long semanticEpoch = 1L;\n    private long activeSemanticEpoch = -1L;",
    "semantic epoch fields",
)

service = replace_once(
    service,
    '        clearFallbackTurnAudio();\n        realtimePartial = "";\n\n        // New speech immediately makes older generated work stale.',
    '        clearFallbackTurnAudio();\n        realtimePartial = "";\n        semanticEpoch++;\n\n        // New speech immediately makes older generated work stale.',
    "new speech semantic epoch",
)

service = replace_once(
    service,
    "        if (!useful.isEmpty()) {\n            if (semanticFallback != null && activeSemanticRequestId >= 0L) {",
    "        if (!useful.isEmpty()) {\n            semanticEpoch++;\n            if (semanticFallback != null && activeSemanticRequestId >= 0L) {",
    "accepted transcript semantic epoch",
)

status_handler = '''    @Override
    public void onStatus(long sessionSerial, long workSerial, boolean replyWork, String status) {
        getMainExecutor().execute(() ->
                handleFileStatus(sessionSerial, workSerial, replyWork, status));
    }

    private synchronized void handleFileStatus(
            long sessionSerial, long workSerial, boolean replyWork, String status) {
        if (aiClient == null) return;
        boolean current = replyWork
                ? aiClient.isReplyCallbackCurrent(sessionSerial, workSerial)
                : aiClient.isTranscriptCallbackCurrent(sessionSerial, workSerial);
        if (!current || engine == null || !engine.isRunning()) return;

        String raw = status == null ? "" : status.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("мисля") || lower.contains("генерирам")) {
            thinkingStartedAtMs = System.currentTimeMillis();
            lastFirstReplyLatencyMs = -1L;
            lastStylesLatencyMs = -1L;
        }
        aiStatus = simplifyStatus(raw);
        renderStatus();
        renderDebug();
        if (isListeningStatus(raw)) scheduleSemanticFallbackIfNeeded();
    }
'''
service = replace_between(
    service,
    "    @Override\n    public void onStatus(",
    "\n    private void scheduleSemanticFallbackIfNeeded()",
    status_handler,
    "status handler",
)

semantic_methods = '''    private void scheduleSemanticFallbackIfNeeded() {
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

        String focusSnapshot = pendingSemanticFocus;
        long focusTimeSnapshot = pendingSemanticAtMs;
        long semanticEpochSnapshot = semanticEpoch;
        long minGapMs = SemanticSchedulingPolicy.minGapMs(focusSnapshot);
        long wait = Math.max(0L, minGapMs - (now - lastSemanticRequestAtMs));
        if (wait > 0L) {
            overlay.postDelayed(() -> {
                if (SemanticEpochPolicy.shouldRun(semanticEpochSnapshot, semanticEpoch)
                        && focusSnapshot.equals(pendingSemanticFocus)
                        && focusTimeSnapshot == pendingSemanticAtMs
                        && answerUpdatedAtMs < focusTimeSnapshot) {
                    requestSemanticFallback(focusSnapshot, focusTimeSnapshot, semanticEpochSnapshot);
                }
            }, wait);
            return;
        }
        requestSemanticFallback(focusSnapshot, focusTimeSnapshot, semanticEpochSnapshot);
    }

    private void requestSemanticFallback(String focus, long focusAtMs, long expectedSemanticEpoch) {
        if (engine == null || !engine.isRunning() || semanticFallback == null) return;
        if (!SemanticEpochPolicy.shouldRun(expectedSemanticEpoch, semanticEpoch)) return;
        long now = System.currentTimeMillis();
        if (focus == null || focus.isEmpty() || now - focusAtMs > SEMANTIC_FOCUS_MAX_AGE_MS) return;
        if (answerUpdatedAtMs >= focusAtMs) return;
        if (now - lastSemanticRequestAtMs < SemanticSchedulingPolicy.minGapMs(focus)) return;

        lastSemanticRequestAtMs = now;
        semanticAnswerBaselineMs = answerUpdatedAtMs;
        semanticPrimaryAppliedAtMs = 0L;
        long requestId = semanticFallback.request(
                buildSemanticContext(), focus, currentVisibleSuggestion());
        activeSemanticRequestId = requestId;
        activeSemanticEpoch = requestId >= 0L ? expectedSemanticEpoch : -1L;
        pendingSemanticFocus = "";
    }
'''
service = replace_between(
    service,
    "    private void scheduleSemanticFallbackIfNeeded() {",
    "\n    private void applySemanticReply",
    semantic_methods,
    "semantic scheduling methods",
)

service = replace_once(
    service,
    "        if (requestId < 0L || requestId != activeSemanticRequestId) return;",
    "        if (requestId < 0L || requestId != activeSemanticRequestId) return;\n        if (!SemanticEpochPolicy.shouldRun(activeSemanticEpoch, semanticEpoch)) return;",
    "semantic callback epoch check",
)

# Every explicit semantic-request invalidation also invalidates its captured epoch.
service = re.sub(
    r"(?m)^(\s*)activeSemanticRequestId = -1L;\n(?!\1activeSemanticEpoch = -1L;)",
    lambda m: m.group(0) + m.group(1) + "activeSemanticEpoch = -1L;\n",
    service,
)

service = replace_once(
    service,
    "        if (engine.isRunning()) {\n            if (aiClient != null) aiClient.invalidatePendingWork();",
    "        if (engine.isRunning()) {\n            semanticEpoch++;\n            if (aiClient != null) aiClient.invalidatePendingWork();",
    "pause semantic epoch",
)
service = replace_once(
    service,
    "        long now = System.currentTimeMillis();\n        boolean resetContext = !hasStartedSession || pausedAtMs == 0L || now - pausedAtMs >= CONTEXT_RESET_AFTER_PAUSE_MS;",
    "        long now = System.currentTimeMillis();\n        semanticEpoch++;\n        boolean resetContext = !hasStartedSession || pausedAtMs == 0L || now - pausedAtMs >= CONTEXT_RESET_AFTER_PAUSE_MS;",
    "resume semantic epoch",
)
service_path.write_text(service)


Path("app/src/main/java/com/livecopilot/micprobe/SemanticEpochPolicy.java").write_text(
    '''package com.livecopilot.micprobe;\n\nfinal class SemanticEpochPolicy {\n    private SemanticEpochPolicy() {}\n\n    static boolean shouldRun(long scheduledEpoch, long currentEpoch) {\n        return scheduledEpoch > 0L && scheduledEpoch == currentEpoch;\n    }\n}\n'''
)

Path("app/src/test/java/com/livecopilot/micprobe/SemanticEpochPolicyTest.java").write_text(
    '''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n\npublic class SemanticEpochPolicyTest {\n    @Test\n    public void currentEpochRuns() {\n        assertTrue(SemanticEpochPolicy.shouldRun(8L, 8L));\n    }\n\n    @Test\n    public void olderEpochIsRejected() {\n        assertFalse(SemanticEpochPolicy.shouldRun(7L, 8L));\n    }\n\n    @Test\n    public void invalidEpochIsRejected() {\n        assertFalse(SemanticEpochPolicy.shouldRun(0L, 8L));\n        assertFalse(SemanticEpochPolicy.shouldRun(-1L, -1L));\n    }\n}\n'''
)

gradle_path = Path("app/build.gradle.kts")
gradle = gradle_path.read_text()
gradle = replace_once(gradle, "versionCode = 53", "versionCode = 54", "versionCode")
gradle = replace_once(
    gradle,
    'versionName = "0.52.0-file-callback-freshness"',
    'versionName = "0.53.0-status-semantic-freshness"',
    "versionName",
)
gradle_path.write_text(gradle)
