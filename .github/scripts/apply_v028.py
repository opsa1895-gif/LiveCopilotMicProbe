from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:140]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


semantic = "app/src/main/java/com/livecopilot/micprobe/SemanticReplyFallback.java"
gradle = "app/build.gradle.kts"

replace_once(
    semantic,
    '''    private static final long MAX_REQUEST_AGE_MS = 12_000L;
''',
    '''    private static final long MAX_REQUEST_AGE_MS = 12_000L;
    private static final int DECISION_CONNECT_TIMEOUT_MS = 4_000;
    private static final int DECISION_READ_TIMEOUT_MS = 7_000;
    private static final int STYLE_CONNECT_TIMEOUT_MS = 5_000;
    private static final int STYLE_READ_TIMEOUT_MS = 10_000;
''',
)

replace_once(
    semantic,
    '''            HttpResult result = post(req, key, 12_000);
''',
    '''            HttpResult result = post(
                    req, key, DECISION_CONNECT_TIMEOUT_MS, DECISION_READ_TIMEOUT_MS);
''',
)

replace_once(
    semantic,
    '''            HttpResult result = post(req, key, 18_000);
''',
    '''            HttpResult result = post(
                    req, key, STYLE_CONNECT_TIMEOUT_MS, STYLE_READ_TIMEOUT_MS);
''',
)

replace_once(
    semantic,
    '''    private HttpResult post(JSONObject req, String key, int readTimeoutMs) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
            c.setConnectTimeout(8_000);
            c.setReadTimeout(readTimeoutMs);
''',
    '''    private HttpResult post(JSONObject req, String key, int connectTimeoutMs,
                            int readTimeoutMs) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
            c.setConnectTimeout(connectTimeoutMs);
            c.setReadTimeout(readTimeoutMs);
''',
)

replace_once(
    gradle,
    '''        versionCode = 27
        versionName = "0.27.0-semantic-novelty"
''',
    '''        versionCode = 28
        versionName = "0.28.0-semantic-deadlines"
''',
)
