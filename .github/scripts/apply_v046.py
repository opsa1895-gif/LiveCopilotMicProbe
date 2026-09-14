from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)

client_path = Path("app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java")
client = client_path.read_text()

client = replace_once(
    client,
    '''            if (actionable || engagement) queueReply(focus, engagement);\n            else listener.onStatus("Слушам");''',
    '''            if (actionable || engagement) {\n                if (!queueReplyIfFresh(item, focus, engagement)) listener.onStatus("Слушам");\n            } else listener.onStatus("Слушам");''',
    "reply call",
)

client = replace_once(
    client,
    '''    private void queueReply(String focus, boolean engagement) {\n        ReplyJob job;\n        synchronized (this) {\n            long serial = ++latestReplySerial;\n            job = new ReplyJob(serial, sessionSerial, contextTextLocked(), focus, lastDirectReply, engagement);\n        }\n        replyExecutor.execute(() -> processReply(job));\n    }''',
    '''    private boolean queueReplyIfFresh(AudioItem item, String focus, boolean engagement) {\n        ReplyJob job;\n        synchronized (this) {\n            // Re-check freshness atomically with reply-job creation. Without this, a\n            // newer input can arrive after the earlier surface check and an old file\n            // transcript can issue a fresh reply serial over the newer conversation.\n            if (!isFreshAudioResultLocked(item)) return false;\n            long serial = ++latestReplySerial;\n            job = new ReplyJob(serial, sessionSerial, contextTextLocked(), focus, lastDirectReply, engagement);\n        }\n        replyExecutor.execute(() -> processReply(job));\n        return true;\n    }''',
    "queueReply",
)

client = replace_once(
    client,
    '''    private synchronized boolean shouldSurfaceAudioResult(AudioItem item) {\n        long ageMs = Math.max(0L, System.currentTimeMillis() - item.createdAtMs);\n        boolean sessionMatches = !closed && item.sessionSerial == sessionSerial;\n        return FileSttFreshnessPolicy.shouldSurface(\n                item.inputSerial, latestInputSerial, ageMs, sessionMatches);\n    }''',
    '''    private synchronized boolean shouldSurfaceAudioResult(AudioItem item) {\n        return isFreshAudioResultLocked(item);\n    }\n\n    private boolean isFreshAudioResultLocked(AudioItem item) {\n        long ageMs = Math.max(0L, System.currentTimeMillis() - item.createdAtMs);\n        boolean sessionMatches = !closed && item.sessionSerial == sessionSerial;\n        return FileSttFreshnessPolicy.shouldSurface(\n                item.inputSerial, latestInputSerial, ageMs, sessionMatches);\n    }''',
    "freshness helper",
)

client_path.write_text(client)

test_path = Path("app/src/test/java/com/livecopilot/micprobe/FileSttFreshnessPolicyTest.java")
test = test_path.read_text()
marker = '''    @Test\n    public void oldSessionCannotSurface() {\n        assertFalse(FileSttFreshnessPolicy.shouldSurface(4L, 4L, 1_000L, false));\n    }\n}'''
replacement = '''    @Test\n    public void oldSessionCannotSurface() {\n        assertFalse(FileSttFreshnessPolicy.shouldSurface(4L, 4L, 1_000L, false));\n    }\n\n    @Test\n    public void queueTimeRecheckRejectsItemAfterSerialAdvances() {\n        long itemSerial = 7L;\n        assertTrue(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial, 300L, true));\n        assertFalse(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial + 1L, 301L, true));\n    }\n}'''
test = replace_once(test, marker, replacement, "freshness regression test")
test_path.write_text(test)

gradle_path = Path("app/build.gradle.kts")
gradle = gradle_path.read_text()
gradle = replace_once(gradle, 'versionCode = 46', 'versionCode = 47', 'versionCode')
gradle = replace_once(
    gradle,
    'versionName = "0.45.0-file-stt-freshness"',
    'versionName = "0.46.0-atomic-reply-freshness"',
    'versionName',
)
gradle_path.write_text(gradle)
