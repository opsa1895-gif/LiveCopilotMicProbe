from pathlib import Path


def one(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 occurrence, found {count}")
    return text.replace(old, new, 1)


p = Path("app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java")
s = p.read_text()

s = one(
    s,
    "        final long inputSerial;\n\n"
    "        AudioItem(short[] samples, int sampleRate, long sessionSerial, long inputSerial) {\n"
    "            this.samples = samples;\n"
    "            this.sampleRate = sampleRate;\n"
    "            this.createdAtMs = System.currentTimeMillis();\n"
    "            this.sessionSerial = sessionSerial;\n"
    "            this.inputSerial = inputSerial;\n"
    "        }\n"
    "    }",
    "        final long inputSerial;\n"
    "        private volatile HttpURLConnection activeConnection;\n"
    "        private volatile boolean cancelled;\n\n"
    "        AudioItem(short[] samples, int sampleRate, long sessionSerial, long inputSerial) {\n"
    "            this.samples = samples;\n"
    "            this.sampleRate = sampleRate;\n"
    "            this.createdAtMs = System.currentTimeMillis();\n"
    "            this.sessionSerial = sessionSerial;\n"
    "            this.inputSerial = inputSerial;\n"
    "        }\n\n"
    "        synchronized void attachConnection(HttpURLConnection connection) {\n"
    "            if (connection == null) return;\n"
    "            if (cancelled) {\n"
    "                try { connection.disconnect(); } catch (Throwable ignored) {}\n"
    "                return;\n"
    "            }\n"
    "            activeConnection = connection;\n"
    "        }\n\n"
    "        synchronized void detachConnection(HttpURLConnection connection) {\n"
    "            if (activeConnection == connection) activeConnection = null;\n"
    "        }\n\n"
    "        synchronized void cancel() {\n"
    "            cancelled = true;\n"
    "            HttpURLConnection connection = activeConnection;\n"
    "            activeConnection = null;\n"
    "            if (connection != null) {\n"
    "                try { connection.disconnect(); } catch (Throwable ignored) {}\n"
    "            }\n"
    "        }\n\n"
    "        boolean isCancelled() {\n"
    "            return cancelled;\n"
    "        }\n"
    "    }",
    "audio item cancellation",
)

s = one(
    s,
    "    private static final long SELF_ECHO_WINDOW_MS = 12_000L;\n"
    "    private static final int PRIMARY_CONNECT_TIMEOUT_MS = 5_000;",
    "    private static final long SELF_ECHO_WINDOW_MS = 12_000L;\n"
    "    // Absolute from chunk submission, so ordered waits cannot stack this delay\n"
    "    // once per chunk at the end of a long speech turn.\n"
    "    private static final long FILE_STT_COMMIT_DEADLINE_MS = 8_000L;\n"
    "    private static final int PRIMARY_CONNECT_TIMEOUT_MS = 5_000;",
    "deadline constant",
)

s = one(
    s,
    "        audioExecutor.submit(\n"
    "                () -> transcribeAudioIfFresh(item),\n"
    "                (raw, error) -> processAudioResult(item, raw, error));",
    "        audioExecutor.submit(\n"
    "                () -> transcribeAudioIfFresh(item),\n"
    "                (raw, error) -> {\n"
    "                    if (error instanceof java.util.concurrent.TimeoutException) {\n"
    "                        // Drop only the overdue chunk. Later same-turn chunks can\n"
    "                        // still commit in order and feed the final turn reply.\n"
    "                        item.cancel();\n"
    "                        listener.onStatus(item.sessionSerial, item.inputSerial, false, \"Слушам\");\n"
    "                        return;\n"
    "                    }\n"
    "                    processAudioResult(item, raw, error);\n"
    "                },\n"
    "                FILE_STT_COMMIT_DEADLINE_MS);",
    "bounded audio submit",
)

s = one(
    s,
    "                // A newer utterance cancelled this request. Never retry stale audio,\n"
    "                // otherwise the single-thread worker can still be blocked by obsolete STT.",
    "                // A newer utterance or the bounded commit deadline cancelled this request.\n"
    "                // Never retry stale audio and keep the parallel STT workers available.",
    "retry comment",
)

s = one(
    s,
    "            c = connection(\"https://api.openai.com/v1/audio/transcriptions\", key, 40_000);\n"
    "            c.setRequestProperty(\"Content-Type\", \"multipart/form-data; boundary=\" + boundary);\n"
    "            audioHttp.register(c);\n"
    "            // Covers invalidation after the preflight but before registration became",
    "            c = connection(\"https://api.openai.com/v1/audio/transcriptions\", key, 40_000);\n"
    "            c.setRequestProperty(\"Content-Type\", \"multipart/form-data; boundary=\" + boundary);\n"
    "            audioHttp.register(c);\n"
    "            item.attachConnection(c);\n"
    "            // Covers invalidation after the preflight but before registration became",
    "attach STT connection",
)

s = one(
    s,
    "            if (c != null) {\n"
    "                audioHttp.unregister(c);\n"
    "                try { c.disconnect(); } catch (Throwable ignored) {}\n"
    "            }\n"
    "        }\n"
    "    }\n\n"
    "    private String primaryReply",
    "            if (c != null) {\n"
    "                item.detachConnection(c);\n"
    "                audioHttp.unregister(c);\n"
    "                try { c.disconnect(); } catch (Throwable ignored) {}\n"
    "            }\n"
    "        }\n"
    "    }\n\n"
    "    private String primaryReply",
    "detach STT connection",
)

s = one(
    s,
    "    private boolean isProcessableAudioResultLocked(AudioItem item) {\n"
    "        long ageMs = Math.max(0L, System.currentTimeMillis() - item.createdAtMs);",
    "    private boolean isProcessableAudioResultLocked(AudioItem item) {\n"
    "        if (item == null || item.isCancelled()) return false;\n"
    "        long ageMs = Math.max(0L, System.currentTimeMillis() - item.createdAtMs);",
    "cancelled freshness",
)

p.write_text(s)
