from pathlib import Path

source_path = Path('app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java')
source = source_path.read_text()

replacements = [
    ('    private static final int MAX_AUDIO_QUEUE = 2;\n', ''),
    ('    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();',
     '    private final LatestWinsExecutor audioExecutor = new LatestWinsExecutor(1);'),
    ('    private final Deque<AudioItem> audioQueue = new ArrayDeque<>();\n', ''),
    ('    private boolean audioWorkerRunning;\n', ''),
]
for old, new in replacements:
    if source.count(old) != 1:
        raise SystemExit(f'unexpected source replacement count for {old!r}: {source.count(old)}')
    source = source.replace(old, new, 1)

if source.count('        audioQueue.clear();\n') != 3:
    raise SystemExit(f'unexpected audioQueue.clear count: {source.count("        audioQueue.clear();\\n")}')
source = source.replace('        audioQueue.clear();\n', '')

old_submit = '''    synchronized void submitAudio(short[] samples, int sampleRate) {
        if (closed || samples == null || samples.length == 0) return;
        long now = System.currentTimeMillis();
        long inputSerial = ++latestInputSerial;
        // New file audio supersedes any older in-flight transcription as well as
        // reply work derived from older audio.
        latestReplySerial++;
        audioHttp.cancelAll();
        replyHttp.cancelAll();
        while (!audioQueue.isEmpty() && now - audioQueue.peekFirst().createdAtMs > MAX_AUDIO_AGE_MS) {
            audioQueue.removeFirst();
        }
        while (audioQueue.size() >= MAX_AUDIO_QUEUE) audioQueue.removeFirst();
        audioQueue.addLast(new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial));
        if (!audioWorkerRunning) {
            audioWorkerRunning = true;
            audioExecutor.execute(this::drainAudio);
        }
    }

    private void drainAudio() {
        while (true) {
            AudioItem item;
            synchronized (this) {
                item = audioQueue.pollFirst();
                if (item == null || closed) {
                    audioWorkerRunning = false;
                    return;
                }
            }
            if (System.currentTimeMillis() - item.createdAtMs > MAX_AUDIO_AGE_MS) continue;
            if (!isCurrentSession(item.sessionSerial)) continue;
            processAudio(item);
        }
    }

    private void processAudio(AudioItem item) {'''

new_submit = '''    synchronized void submitAudio(short[] samples, int sampleRate) {
        if (closed || samples == null || samples.length == 0) return;
        long inputSerial = ++latestInputSerial;
        // New file audio supersedes any older in-flight transcription as well as
        // reply work derived from older audio. LatestWinsExecutor also keeps only
        // one pending task, so bursty fallback audio cannot build a stale backlog.
        latestReplySerial++;
        audioHttp.cancelAll();
        replyHttp.cancelAll();
        AudioItem item = new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial);
        audioExecutor.execute(() -> processAudioIfFresh(item));
    }

    private void processAudioIfFresh(AudioItem item) {
        // A queued task may have been superseded before the worker became free.
        // Reject it before API-key loading, status callbacks, or network work.
        if (item == null || !shouldSurfaceAudioResult(item)) return;
        processAudio(item);
    }

    private void processAudio(AudioItem item) {'''

if source.count(old_submit) != 1:
    raise SystemExit(f'unexpected submit/drain block count: {source.count(old_submit)}')
source = source.replace(old_submit, new_submit, 1)
source_path.write_text(source)

# Add deterministic coverage for the one-pending-task latest-wins executor behavior.
test_path = Path('app/src/test/java/com/livecopilot/micprobe/LatestWinsExecutorTest.java')
if test_path.exists():
    raise SystemExit('LatestWinsExecutorTest.java already exists')
test_path.write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport java.util.concurrent.CountDownLatch;\nimport java.util.concurrent.TimeUnit;\nimport java.util.concurrent.atomic.AtomicBoolean;\n\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n\npublic class LatestWinsExecutorTest {\n    @Test\n    public void newestPendingTaskReplacesOlderQueuedTask() throws Exception {\n        LatestWinsExecutor executor = new LatestWinsExecutor(1);\n        CountDownLatch firstStarted = new CountDownLatch(1);\n        CountDownLatch releaseFirst = new CountDownLatch(1);\n        CountDownLatch newestFinished = new CountDownLatch(1);\n        AtomicBoolean middleRan = new AtomicBoolean(false);\n\n        try {\n            executor.execute(() -> {\n                firstStarted.countDown();\n                try {\n                    releaseFirst.await(2, TimeUnit.SECONDS);\n                } catch (InterruptedException ignored) {\n                    Thread.currentThread().interrupt();\n                }\n            });\n            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));\n\n            executor.execute(() -> middleRan.set(true));\n            executor.execute(newestFinished::countDown);\n\n            releaseFirst.countDown();\n            assertTrue(newestFinished.await(2, TimeUnit.SECONDS));\n            assertFalse(middleRan.get());\n        } finally {\n            releaseFirst.countDown();\n            executor.shutdownNow();\n        }\n    }\n}\n''')

gradle_path = Path('app/build.gradle.kts')
gradle = gradle_path.read_text()
old_version = '''        versionCode = 58
        versionName = "0.57.0-cancel-stale-file-stt"'''
new_version = '''        versionCode = 59
        versionName = "0.58.0-latest-file-stt-queue"'''
if gradle.count(old_version) != 1:
    raise SystemExit('unexpected app version block')
gradle_path.write_text(gradle.replace(old_version, new_version, 1))
