from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected one match for {label}, found {count}")
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
    "    private final ExecutorService variantExecutor = Executors.newSingleThreadExecutor();\n    private final Deque<AudioItem> audioQueue = new ArrayDeque<>();",
    "    private final ExecutorService variantExecutor = Executors.newSingleThreadExecutor();\n    private final HttpConnectionRegistry replyHttp = new HttpConnectionRegistry();\n    private final Deque<AudioItem> audioQueue = new ArrayDeque<>();",
    "reply HTTP registry field",
)

client = replace_once(
    client,
    "    synchronized void resetSession() {\n        sessionSerial++;",
    "    synchronized void resetSession() {\n        sessionSerial++;\n        replyHttp.cancelAll();",
    "reset cancels reply HTTP",
)

client = replace_once(
    client,
    "        latestInputSerial++;\n        latestReplySerial++;\n    }\n\n    synchronized void invalidatePendingWork()",
    "        latestInputSerial++;\n        latestReplySerial++;\n        replyHttp.cancelAll();\n    }\n\n    synchronized void invalidatePendingWork()",
    "new speech cancels reply HTTP",
)

client = replace_once(
    client,
    "        latestInputSerial++;\n        latestReplySerial++;\n        audioQueue.clear();\n    }\n\n    synchronized boolean isTranscriptCallbackCurrent",
    "        latestInputSerial++;\n        latestReplySerial++;\n        replyHttp.cancelAll();\n        audioQueue.clear();\n    }\n\n    synchronized boolean isTranscriptCallbackCurrent",
    "pause invalidation cancels reply HTTP",
)

client = replace_once(
    client,
    "            latestInputSerial++;\n            latestReplySerial++;\n            if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;",
    "            latestInputSerial++;\n            latestReplySerial++;\n            replyHttp.cancelAll();\n            if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;",
    "accepted realtime transcript cancels reply HTTP",
)

client = replace_once(
    client,
    "        latestReplySerial++;\n        if (!direct.isEmpty()) lastDirectReply = shorten(direct, 180);",
    "        latestReplySerial++;\n        replyHttp.cancelAll();\n        if (!direct.isEmpty()) lastDirectReply = shorten(direct, 180);",
    "external reply cancels file reply HTTP",
)

client = replace_once(
    client,
    "        // New speech immediately invalidates reply work from older file audio.\n        latestReplySerial++;",
    "        // New speech immediately invalidates reply work from older file audio.\n        latestReplySerial++;\n        replyHttp.cancelAll();",
    "file audio cancels older reply HTTP",
)

client = replace_once(
    client,
    "        firstSpeechAtMs = 0L;\n        latestReplySerial++;\n    }",
    "        firstSpeechAtMs = 0L;\n        latestReplySerial++;\n        replyHttp.cancelAll();\n    }",
    "topic reset cancels reply HTTP",
)

client = replace_once(
    client,
    "            if (!isFreshAudioResultLocked(item)) return false;\n            long serial = ++latestReplySerial;\n            job = new ReplyJob(serial, sessionSerial, contextTextLocked(), focus, lastDirectReply, engagement);",
    "            if (!isFreshAudioResultLocked(item)) return false;\n            long serial = ++latestReplySerial;\n            replyHttp.cancelAll();\n            job = new ReplyJob(serial, sessionSerial, contextTextLocked(), focus, lastDirectReply, engagement);",
    "new reply job cancels older reply HTTP",
)

client = replace_once(
    client,
    "    private void processReply(ReplyJob job) {\n        String key = SecretStore.loadApiKey(context);\n        if (key.isEmpty()) return;",
    "    private void processReply(ReplyJob job) {\n        if (!current(job)) return;\n        String key = SecretStore.loadApiKey(context);\n        if (key.isEmpty() || !current(job)) return;",
    "reply preflight freshness",
)

client = replace_once(
    client,
    "            primary = primaryReply(key, job.context, job.focus, job.previousSuggestion, job.engagement, false);",
    "            primary = primaryReply(key, job.context, job.focus, job.previousSuggestion, job.engagement, false, job);",
    "primary reply job token",
)

client = replace_once(
    client,
    "                    job.engagement,\n                    varyDirect);",
    "                    job.engagement,\n                    varyDirect,\n                    job);",
    "variant reply job token",
)

client = replace_once(
    client,
    "        audioExecutor.shutdownNow();\n        replyExecutor.shutdownNow();",
    "        replyHttp.cancelAll();\n        audioExecutor.shutdownNow();\n        replyExecutor.shutdownNow();",
    "shutdown cancels reply HTTP",
)

client = replace_once(
    client,
    "    private String primaryReply(String key, String fullContext, String focus,\n                                String previousSuggestion, boolean engagement,\n                                boolean forceVariation) throws Exception {",
    "    private String primaryReply(String key, String fullContext, String focus,\n                                String previousSuggestion, boolean engagement,\n                                boolean forceVariation, ReplyJob job) throws Exception {",
    "primary signature",
)

client = replace_once(
    client,
    "        HttpResult r = responses(key, request(system, user, 90),\n                PRIMARY_CONNECT_TIMEOUT_MS, PRIMARY_READ_TIMEOUT_MS, 1);",
    "        HttpResult r = responses(key, request(system, user, 90),\n                PRIMARY_CONNECT_TIMEOUT_MS, PRIMARY_READ_TIMEOUT_MS, 1, job);",
    "primary responses token",
)

client = replace_once(
    client,
    "    private Replies variants(String key, String fullContext, String focus, String direct,\n                             String previousSuggestion, boolean engagement,\n                             boolean varyDirect) throws Exception {",
    "    private Replies variants(String key, String fullContext, String focus, String direct,\n                             String previousSuggestion, boolean engagement,\n                             boolean varyDirect, ReplyJob job) throws Exception {",
    "variants signature",
)

client = replace_once(
    client,
    "        HttpResult r = responses(key, request(system, user, 250),\n                STYLE_CONNECT_TIMEOUT_MS, STYLE_READ_TIMEOUT_MS, 1);",
    "        HttpResult r = responses(key, request(system, user, 250),\n                STYLE_CONNECT_TIMEOUT_MS, STYLE_READ_TIMEOUT_MS, 1, job);",
    "variant responses token",
)

responses_method = '''    private HttpResult responses(String key, JSONObject req, int connectTimeoutMs,
                                 int readTimeoutMs, int maxAttempts, ReplyJob job) throws Exception {
        int attempts = Math.max(1, maxAttempts);
        Exception last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            HttpURLConnection c = null;
            try {
                if (!current(job)) throw new java.io.InterruptedIOException("stale reply");
                c = connection("https://api.openai.com/v1/responses", key, readTimeoutMs);
                c.setConnectTimeout(Math.max(1_000, connectTimeoutMs));
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                replyHttp.register(c);
                // Covers the race where invalidation happened after the preflight but
                // before this connection became visible to cancelAll().
                if (!current(job)) throw new java.io.InterruptedIOException("stale reply");
                c.getOutputStream().write(req.toString().getBytes(StandardCharsets.UTF_8));
                int code = c.getResponseCode();
                String body = read(c, code);
                boolean retryable = code == 429 || code >= 500;
                if (retryable && attempt + 1 < attempts) {
                    Thread.sleep(350L);
                    continue;
                }
                return new HttpResult(code, body);
            } catch (Exception e) {
                last = e;
                if (!current(job)) throw e;
                if (attempt + 1 < attempts) Thread.sleep(350L);
            } finally {
                if (c != null) {
                    replyHttp.unregister(c);
                    try { c.disconnect(); } catch (Throwable ignored) {}
                }
            }
        }
        throw last == null ? new IllegalStateException("responses") : last;
    }
'''
client = replace_between(
    client,
    "    private HttpResult responses(String key, JSONObject req, int connectTimeoutMs,",
    "\n    private static HttpURLConnection connection",
    responses_method,
    "responses method",
)

client_path.write_text(client)

semantic_path = Path("app/src/main/java/com/livecopilot/micprobe/SemanticReplyFallback.java")
semantic = semantic_path.read_text()

semantic = replace_once(
    semantic,
    "    private final LatestWinsExecutor decisionExecutor = new LatestWinsExecutor(2);\n    private final LatestWinsExecutor variantExecutor = new LatestWinsExecutor(1);",
    "    private final LatestWinsExecutor decisionExecutor = new LatestWinsExecutor(2);\n    private final LatestWinsExecutor variantExecutor = new LatestWinsExecutor(1);\n    private final HttpConnectionRegistry decisionHttp = new HttpConnectionRegistry();\n    private final HttpConnectionRegistry variantHttp = new HttpConnectionRegistry();",
    "semantic HTTP registry fields",
)

semantic = replace_once(
    semantic,
    "        if (closed) return -1L;\n        long requestId = ++serial;",
    "        if (closed) return -1L;\n        long requestId = ++serial;\n        decisionHttp.cancelAll();\n        variantHttp.cancelAll();",
    "new semantic request cancels old HTTP",
)

semantic = replace_once(
    semantic,
    "    synchronized void invalidate() {\n        serial++;\n    }",
    "    synchronized void invalidate() {\n        serial++;\n        decisionHttp.cancelAll();\n        variantHttp.cancelAll();\n    }",
    "semantic invalidate cancels HTTP",
)

semantic = replace_once(
    semantic,
    "        closed = true;\n        serial++;\n        decisionExecutor.shutdownNow();",
    "        closed = true;\n        serial++;\n        decisionHttp.cancelAll();\n        variantHttp.cancelAll();\n        decisionExecutor.shutdownNow();",
    "semantic shutdown cancels HTTP",
)

semantic = replace_once(
    semantic,
    "            HttpResult result = post(\n                    req, key, DECISION_CONNECT_TIMEOUT_MS, DECISION_READ_TIMEOUT_MS);",
    "            HttpResult result = post(\n                    req, key, DECISION_CONNECT_TIMEOUT_MS, DECISION_READ_TIMEOUT_MS,\n                    decisionHttp, requestId, createdAt);",
    "semantic decision post token",
)

semantic = replace_once(
    semantic,
    "            HttpResult result = post(\n                    req, key, STYLE_CONNECT_TIMEOUT_MS, STYLE_READ_TIMEOUT_MS);",
    "            HttpResult result = post(\n                    req, key, STYLE_CONNECT_TIMEOUT_MS, STYLE_READ_TIMEOUT_MS,\n                    variantHttp, requestId, createdAt);",
    "semantic variant post token",
)

semantic_post = '''    private HttpResult post(JSONObject req, String key, int connectTimeoutMs,
                            int readTimeoutMs, HttpConnectionRegistry registry,
                            long requestId, long createdAt) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
            c.setConnectTimeout(connectTimeoutMs);
            c.setReadTimeout(readTimeoutMs);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Connection", "keep-alive");
            registry.register(c);
            // Covers invalidation racing between runDecision/runVariants preflight and
            // connection registration. Once registered, cancelAll() can abort in-flight IO.
            if (!isCurrent(requestId, createdAt)) {
                throw new java.io.InterruptedIOException("stale semantic request");
            }
            c.getOutputStream().write(req.toString().getBytes(StandardCharsets.UTF_8));
            int code = c.getResponseCode();
            return new HttpResult(code, read(c, code));
        } finally {
            if (c != null) {
                registry.unregister(c);
                try { c.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }
'''
semantic = replace_between(
    semantic,
    "    private HttpResult post(JSONObject req, String key, int connectTimeoutMs,",
    "\n    private synchronized boolean isCurrent",
    semantic_post,
    "semantic post method",
)

semantic_path.write_text(semantic)

Path("app/src/main/java/com/livecopilot/micprobe/HttpConnectionRegistry.java").write_text('''package com.livecopilot.micprobe;\n\nimport java.net.HttpURLConnection;\nimport java.util.ArrayList;\nimport java.util.HashSet;\nimport java.util.List;\nimport java.util.Set;\n\nfinal class HttpConnectionRegistry {\n    private final Set<HttpURLConnection> active = new HashSet<>();\n\n    synchronized void register(HttpURLConnection connection) {\n        if (connection != null) active.add(connection);\n    }\n\n    synchronized void unregister(HttpURLConnection connection) {\n        if (connection != null) active.remove(connection);\n    }\n\n    int cancelAll() {\n        List<HttpURLConnection> snapshot;\n        synchronized (this) {\n            if (active.isEmpty()) return 0;\n            snapshot = new ArrayList<>(active);\n            active.clear();\n        }\n        for (HttpURLConnection connection : snapshot) {\n            try { connection.disconnect(); } catch (Throwable ignored) {}\n        }\n        return snapshot.size();\n    }\n\n    synchronized int size() {\n        return active.size();\n    }\n}\n''')

Path("app/src/test/java/com/livecopilot/micprobe/HttpConnectionRegistryTest.java").write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport java.io.IOException;\nimport java.net.HttpURLConnection;\nimport java.net.URL;\n\nimport static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n\npublic class HttpConnectionRegistryTest {\n    @Test\n    public void cancelAllDisconnectsEveryRegisteredConnectionAndClearsRegistry() throws Exception {\n        HttpConnectionRegistry registry = new HttpConnectionRegistry();\n        FakeConnection first = new FakeConnection();\n        FakeConnection second = new FakeConnection();\n        registry.register(first);\n        registry.register(second);\n\n        assertEquals(2, registry.size());\n        assertEquals(2, registry.cancelAll());\n        assertTrue(first.disconnected);\n        assertTrue(second.disconnected);\n        assertEquals(0, registry.size());\n        assertEquals(0, registry.cancelAll());\n    }\n\n    @Test\n    public void unregisteredConnectionIsNotCancelled() throws Exception {\n        HttpConnectionRegistry registry = new HttpConnectionRegistry();\n        FakeConnection connection = new FakeConnection();\n        registry.register(connection);\n        registry.unregister(connection);\n\n        assertEquals(0, registry.cancelAll());\n        assertFalse(connection.disconnected);\n    }\n\n    private static final class FakeConnection extends HttpURLConnection {\n        boolean disconnected;\n\n        FakeConnection() throws Exception {\n            super(new URL("http://localhost"));\n        }\n\n        @Override public void disconnect() { disconnected = true; }\n        @Override public boolean usingProxy() { return false; }\n        @Override public void connect() throws IOException {}\n    }\n}\n''')

build_path = Path("app/build.gradle.kts")
build = build_path.read_text()
build = replace_once(build, "        versionCode = 54", "        versionCode = 55", "version code")
build = replace_once(
    build,
    '        versionName = "0.53.0-status-semantic-freshness"',
    '        versionName = "0.54.0-cancel-stale-http"',
    "version name",
)
build_path.write_text(build)
