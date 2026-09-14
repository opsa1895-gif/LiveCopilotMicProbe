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

# Existing coverage proves the one-slot queue discards older pending work with two
# workers. Add the exact single-worker mode now used by file STT.
test_path = Path('app/src/test/java/com/livecopilot/micprobe/LatestWinsExecutorTest.java')
tests = test_path.read_text()
if 'singleWorkerAlsoKeepsOnlyNewestPendingTask' in tests:
    raise SystemExit('single-worker latest-wins test already exists')
if not tests.rstrip().endswith('}'):
    raise SystemExit('unexpected LatestWinsExecutorTest.java ending')
method = '''

    @Test
    public void singleWorkerAlsoKeepsOnlyNewestPendingTask() throws Exception {
        LatestWinsExecutor executor = new LatestWinsExecutor(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch newestRan = new CountDownLatch(1);
        AtomicBoolean oldQueuedRan = new AtomicBoolean(false);

        try {
            executor.execute(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            executor.execute(() -> oldQueuedRan.set(true));
            executor.execute(newestRan::countDown);

            releaseFirst.countDown();
            assertTrue(newestRan.await(2, TimeUnit.SECONDS));
            Thread.sleep(80L);
            assertFalse(oldQueuedRan.get());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }
'''
trimmed = tests.rstrip()
test_path.write_text(trimmed[:-1] + method + '}\n')

gradle_path = Path('app/build.gradle.kts')
gradle = gradle_path.read_text()
old_version = '''        versionCode = 58
        versionName = "0.57.0-cancel-stale-file-stt"'''
new_version = '''        versionCode = 59
        versionName = "0.58.0-latest-file-stt-queue"'''
if gradle.count(old_version) != 1:
    raise SystemExit('unexpected app version block')
gradle_path.write_text(gradle.replace(old_version, new_version, 1))
