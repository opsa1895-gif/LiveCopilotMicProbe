from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


source_path = Path("app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java")
source = source_path.read_text()

source = replace_once(
    source,
    "    private final HttpConnectionRegistry replyHttp = new HttpConnectionRegistry();\n",
    "    private final HttpConnectionRegistry replyHttp = new HttpConnectionRegistry();\n"
    "    private final HttpConnectionRegistry audioHttp = new HttpConnectionRegistry();\n",
    "audio registry field",
)

source = replace_once(
    source,
    "        sessionSerial++;\n        replyHttp.cancelAll();\n        latestInputSerial = 0L;",
    "        sessionSerial++;\n        audioHttp.cancelAll();\n        replyHttp.cancelAll();\n        latestInputSerial = 0L;",
    "resetSession cancellation",
)

source = replace_once(
    source,
    "        latestInputSerial++;\n        latestReplySerial++;\n        replyHttp.cancelAll();\n    }\n\n    synchronized void invalidatePendingWork()",
    "        latestInputSerial++;\n        latestReplySerial++;\n        audioHttp.cancelAll();\n        replyHttp.cancelAll();\n    }\n\n    synchronized void invalidatePendingWork()",
    "noteNewSpeech cancellation",
)

source = replace_once(
    source,
    "        latestInputSerial++;\n        latestReplySerial++;\n        replyHttp.cancelAll();\n        audioQueue.clear();\n    }\n\n    synchronized boolean isTranscriptCallbackCurrent",
    "        latestInputSerial++;\n        latestReplySerial++;\n        audioHttp.cancelAll();\n        replyHttp.cancelAll();\n        audioQueue.clear();\n    }\n\n    synchronized boolean isTranscriptCallbackCurrent",
    "invalidatePendingWork cancellation",
)

source = replace_once(
    source,
    "            latestInputSerial++;\n            latestReplySerial++;\n            replyHttp.cancelAll();\n            if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;",
    "            latestInputSerial++;\n            latestReplySerial++;\n            audioHttp.cancelAll();\n            replyHttp.cancelAll();\n            if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;",
    "accepted realtime transcript cancellation",
)

source = replace_once(
    source,
    "        long inputSerial = ++latestInputSerial;\n        // New speech immediately invalidates reply work from older file audio.\n        latestReplySerial++;\n        replyHttp.cancelAll();",
    "        long inputSerial = ++latestInputSerial;\n        // New file audio supersedes any older in-flight transcription as well as\n        // reply work derived from older audio.\n        latestReplySerial++;\n        audioHttp.cancelAll();\n        replyHttp.cancelAll();",
    "submitAudio cancellation",
)

source = replace_once(
    source,
    "            String raw = transcribe(key, item.samples, item.sampleRate);",
    "            String raw = transcribe(key, item);",
    "processAudio transcribe call",
)

source = replace_once(
    source,
    "        } catch (Throwable ignored) {\n            if (isCurrentSession(item.sessionSerial)) {\n                listener.onStatus(item.sessionSerial, item.inputSerial, false, \"AI връзката прекъсна\");\n            }\n        }",
    "        } catch (Throwable ignored) {\n            // Cancellation caused by newer speech is expected and must not surface\n            // as a connection error for the obsolete audio item.\n            if (shouldSurfaceAudioResult(item)) {\n                listener.onStatus(item.sessionSerial, item.inputSerial, false, \"AI връзката прекъсна\");\n            }\n        }",
    "stale cancellation status suppression",
)

source = replace_once(
    source,
    "        replyHttp.cancelAll();\n        audioExecutor.shutdownNow();",
    "        audioHttp.cancelAll();\n        replyHttp.cancelAll();\n        audioExecutor.shutdownNow();",
    "shutdown cancellation",
)

old_transcribe = '''    private String transcribe(String key, short[] samples, int sampleRate) throws Exception {
        byte[] wav = wav(samples, sampleRate);
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpResult r = transcriptionRequest(key, wav);
                if (r.code >= 200 && r.code < 300) return clean(new JSONObject(r.body).optString("text", ""));
                if (r.code != 429 && r.code < 500) throw new IllegalStateException("STT " + r.code);
                last = new IllegalStateException("STT " + r.code);
            } catch (Exception e) {
                last = e;
            }
            if (attempt == 0) Thread.sleep(500L);
        }
        throw last == null ? new IllegalStateException("STT") : last;
    }

    private HttpResult transcriptionRequest(String key, byte[] wav) throws Exception {
        String boundary = "----LiveCopilot" + System.nanoTime();
        HttpURLConnection c = connection("https://api.openai.com/v1/audio/transcriptions", key, 40_000);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (DataOutputStream out = new DataOutputStream(c.getOutputStream())) {
            field(out, boundary, "model", "gpt-transcribe");
            field(out, boundary, "language", "bg");
            field(out, boundary, "prompt", transcriptionPrompt());
            field(out, boundary, "response_format", "json");
            out.writeBytes("--" + boundary + "\\r\\n");
            out.writeBytes("Content-Disposition: form-data; name=\\"file\\"; filename=\\"live.wav\\"\\r\\n");
            out.writeBytes("Content-Type: audio/wav\\r\\n\\r\\n");
            out.write(wav);
            out.writeBytes("\\r\\n--" + boundary + "--\\r\\n");
        }
        int code = c.getResponseCode();
        return new HttpResult(code, read(c, code));
    }'''

new_transcribe = '''    private String transcribe(String key, AudioItem item) throws Exception {
        byte[] wav = wav(item.samples, item.sampleRate);
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (!shouldSurfaceAudioResult(item)) {
                    throw new java.io.InterruptedIOException("stale transcription");
                }
                HttpResult r = transcriptionRequest(key, wav, item);
                if (r.code >= 200 && r.code < 300) return clean(new JSONObject(r.body).optString("text", ""));
                if (r.code != 429 && r.code < 500) throw new IllegalStateException("STT " + r.code);
                last = new IllegalStateException("STT " + r.code);
            } catch (Exception e) {
                last = e;
                // A newer utterance cancelled this request. Never retry stale audio,
                // otherwise the single-thread worker can still be blocked by obsolete STT.
                if (!shouldSurfaceAudioResult(item)) throw e;
            }
            if (attempt == 0) {
                if (!shouldSurfaceAudioResult(item)) {
                    throw new java.io.InterruptedIOException("stale transcription");
                }
                Thread.sleep(500L);
            }
        }
        throw last == null ? new IllegalStateException("STT") : last;
    }

    private HttpResult transcriptionRequest(String key, byte[] wav, AudioItem item) throws Exception {
        String boundary = "----LiveCopilot" + System.nanoTime();
        HttpURLConnection c = null;
        try {
            if (!shouldSurfaceAudioResult(item)) {
                throw new java.io.InterruptedIOException("stale transcription");
            }
            c = connection("https://api.openai.com/v1/audio/transcriptions", key, 40_000);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            audioHttp.register(c);
            // Covers invalidation after the preflight but before registration became
            // visible to cancelAll().
            if (!shouldSurfaceAudioResult(item)) {
                throw new java.io.InterruptedIOException("stale transcription");
            }
            try (DataOutputStream out = new DataOutputStream(c.getOutputStream())) {
                field(out, boundary, "model", "gpt-transcribe");
                field(out, boundary, "language", "bg");
                field(out, boundary, "prompt", transcriptionPrompt());
                field(out, boundary, "response_format", "json");
                out.writeBytes("--" + boundary + "\\r\\n");
                out.writeBytes("Content-Disposition: form-data; name=\\"file\\"; filename=\\"live.wav\\"\\r\\n");
                out.writeBytes("Content-Type: audio/wav\\r\\n\\r\\n");
                out.write(wav);
                out.writeBytes("\\r\\n--" + boundary + "--\\r\\n");
            }
            int code = c.getResponseCode();
            return new HttpResult(code, read(c, code));
        } finally {
            if (c != null) {
                audioHttp.unregister(c);
                try { c.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }'''

source = replace_once(source, old_transcribe, new_transcribe, "transcription cancellation block")
source_path.write_text(source)

# The existing freshness policy is also the network retry/cancellation gate. Add a
# regression that makes this explicit so future retry changes cannot revive old audio.
test_path = Path("app/src/test/java/com/livecopilot/micprobe/FileSttFreshnessPolicyTest.java")
tests = test_path.read_text()
marker = '''    @Test
    public void contextCommitAndSurfaceShareLatestWinsGate() {
        long itemSerial = 9L;
        assertTrue(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial, 250L, true));
        assertTrue(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial, 250L, true));
        assertFalse(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial + 1L, 251L, true));
        assertFalse(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial + 1L, 251L, true));
    }
}'''
replacement = '''    @Test
    public void contextCommitAndSurfaceShareLatestWinsGate() {
        long itemSerial = 9L;
        assertTrue(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial, 250L, true));
        assertTrue(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial, 250L, true));
        assertFalse(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial + 1L, 251L, true));
        assertFalse(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial + 1L, 251L, true));
    }

    @Test
    public void networkRetryGateRejectsSupersededAudio() {
        long itemSerial = 12L;
        assertTrue(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial, 400L, true));
        assertFalse(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial + 1L, 401L, true));
    }
}'''
tests = replace_once(tests, marker, replacement, "file STT cancellation regression")
test_path.write_text(tests)

gradle_path = Path("app/build.gradle.kts")
gradle = gradle_path.read_text()
gradle = replace_once(
    gradle,
    '        versionCode = 57\n        versionName = "0.56.0-atomic-file-context"',
    '        versionCode = 58\n        versionName = "0.57.0-cancel-stale-file-stt"',
    "version bump",
)
gradle_path.write_text(gradle)
