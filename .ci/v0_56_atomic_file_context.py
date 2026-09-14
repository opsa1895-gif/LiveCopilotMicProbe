from pathlib import Path

source_path = Path('app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java')
source = source_path.read_text()

old = '''            long now = System.currentTimeMillis();
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
            }'''

new = '''            long now = System.currentTimeMillis();
            String focus;
            synchronized (this) {
                // File STT may finish after newer speech has already started. Check
                // freshness while holding the same monitor used for conversation
                // mutations so stale audio cannot alter recentTurns, summary/reset
                // state, or lastTranscriptAtMs between a check and the write.
                if (!isFreshAudioResultLocked(item)) {
                    focus = "";
                } else {
                    String useful = removeRecentSelfEcho(raw, now);
                    if (useful.isEmpty()) {
                        focus = "";
                    } else {
                        prepareContextFor(useful, now);
                        focus = commitTranscript(useful, now);
                        if (!focus.isEmpty() && firstSpeechAtMs == 0L) firstSpeechAtMs = now;
                    }
                }
            }
            if (focus.isEmpty()) {
                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
                return;
            }

            // Re-check after the atomic context commit: newer speech may start after
            // the lock is released, in which case this older transcript must not be
            // surfaced even though it was valid context at commit time.
            if (!shouldSurfaceAudioResult(item)) {
                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
                return;
            }

            listener.onTranscript(item.sessionSerial, item.inputSerial, focus);'''

if source.count(old) != 1:
    raise SystemExit(f'unexpected processAudio block count: {source.count(old)}')
source = source.replace(old, new, 1)

old_policy_call = '''        return FileSttFreshnessPolicy.shouldSurface(
                item.inputSerial, latestInputSerial, ageMs, sessionMatches);'''
new_policy_call = '''        return FileSttFreshnessPolicy.shouldAccept(
                item.inputSerial, latestInputSerial, ageMs, sessionMatches);'''
if source.count(old_policy_call) != 1:
    raise SystemExit('unexpected file STT freshness call')
source_path.write_text(source.replace(old_policy_call, new_policy_call, 1))

policy_path = Path('app/src/main/java/com/livecopilot/micprobe/FileSttFreshnessPolicy.java')
policy = policy_path.read_text()
old_policy = '''    static boolean shouldSurface(long itemSerial, long latestSerial, long ageMs, boolean sessionMatches) {
        return sessionMatches
                && itemSerial > 0L
                && itemSerial == latestSerial
                && ageMs >= 0L
                && ageMs <= MAX_SURFACE_AGE_MS;
    }'''
new_policy = '''    static boolean shouldAccept(long itemSerial, long latestSerial, long ageMs, boolean sessionMatches) {
        return sessionMatches
                && itemSerial > 0L
                && itemSerial == latestSerial
                && ageMs >= 0L
                && ageMs <= MAX_SURFACE_AGE_MS;
    }

    static boolean shouldSurface(long itemSerial, long latestSerial, long ageMs, boolean sessionMatches) {
        return shouldAccept(itemSerial, latestSerial, ageMs, sessionMatches);
    }'''
if policy.count(old_policy) != 1:
    raise SystemExit('unexpected FileSttFreshnessPolicy body')
policy_path.write_text(policy.replace(old_policy, new_policy, 1))

test_path = Path('app/src/test/java/com/livecopilot/micprobe/FileSttFreshnessPolicyTest.java')
tests = test_path.read_text()
marker = '''    @Test
    public void queueTimeRecheckRejectsItemAfterSerialAdvances() {
        long itemSerial = 7L;
        assertTrue(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial, 300L, true));
        assertFalse(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial + 1L, 301L, true));
    }
}'''
replacement = '''    @Test
    public void queueTimeRecheckRejectsItemAfterSerialAdvances() {
        long itemSerial = 7L;
        assertTrue(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial, 300L, true));
        assertFalse(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial + 1L, 301L, true));
    }

    @Test
    public void contextCommitAndSurfaceShareLatestWinsGate() {
        long itemSerial = 9L;
        assertTrue(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial, 250L, true));
        assertTrue(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial, 250L, true));
        assertFalse(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial + 1L, 251L, true));
        assertFalse(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial + 1L, 251L, true));
    }
}'''
if tests.count(marker) != 1:
    raise SystemExit('unexpected FileSttFreshnessPolicyTest tail')
test_path.write_text(tests.replace(marker, replacement, 1))

gradle_path = Path('app/build.gradle.kts')
gradle = gradle_path.read_text()
old_version = '''        versionCode = 56
        versionName = "0.55.0-atomic-reply-state"'''
new_version = '''        versionCode = 57
        versionName = "0.56.0-atomic-file-context"'''
if gradle.count(old_version) != 1:
    raise SystemExit('unexpected app version block')
gradle_path.write_text(gradle.replace(old_version, new_version, 1))
