from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)


client_path = Path('app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java')
client = client_path.read_text()
client = replace_once(
    client,
    '''        void onTranscript(long sessionSerial, long inputSerial, String transcript);\n        void onReplies(long sessionSerial, long replySerial, Replies replies);\n        void onStatus(long sessionSerial, long workSerial, boolean replyWork, String status);''',
    '''        void onTranscript(long sessionSerial, long inputSerial, String transcript);\n        void onReplies(long sessionSerial, long replySerial, Replies replies);\n        void onFileTurnComplete(long sessionSerial, long inputSerial, String turnFocus, boolean mainReplyQueued);\n        void onStatus(long sessionSerial, long workSerial, boolean replyWork, String status);''',
    'listener callback')
client = replace_once(
    client,
    '''        boolean actionable = isActionable(turnFocus);\n        boolean engagement = !actionable && reference > 0L && now - reference >= ENGAGEMENT_GAP_MS;\n        if (!(actionable || engagement)\n                || !queueReplyIfFresh(end, turnFocus, engagement)) {\n            listener.onStatus(end.sessionSerial, end.inputSerial, false, "Слушам");\n        }''',
    '''        boolean actionable = isActionable(turnFocus);\n        boolean engagement = !actionable && reference > 0L && now - reference >= ENGAGEMENT_GAP_MS;\n        boolean mainReplyQueued = (actionable || engagement)\n                && queueReplyIfFresh(end, turnFocus, engagement);\n        // File semantic fallback is also gated by the complete speech turn. The\n        // service re-validates this callback on the main thread before scheduling it.\n        listener.onFileTurnComplete(\n                end.sessionSerial, end.inputSerial, turnFocus, mainReplyQueued);\n        if (!mainReplyQueued) {\n            listener.onStatus(end.sessionSerial, end.inputSerial, false, "Слушам");\n        }''',
    'turn end callback')
client_path.write_text(client)


service_path = Path('app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java')
service = service_path.read_text()
marker = '''    @Override\n    public void onStatus(long sessionSerial, long workSerial, boolean replyWork, String status) {'''
insert = '''    @Override\n    public void onFileTurnComplete(\n            long sessionSerial, long inputSerial, String turnFocus, boolean mainReplyQueued) {\n        getMainExecutor().execute(() -> handleFileTurnComplete(\n                sessionSerial, inputSerial, turnFocus, mainReplyQueued));\n    }\n\n    private synchronized void handleFileTurnComplete(\n            long sessionSerial, long inputSerial, String turnFocus, boolean mainReplyQueued) {\n        if (aiClient == null\n                || !aiClient.isTranscriptCallbackCurrent(sessionSerial, inputSerial)) return;\n        if (engine == null || !engine.isRunning()) return;\n\n        if (!FileTurnReplyPolicy.shouldUseSemanticFallback(mainReplyQueued, turnFocus)) {\n            pendingSemanticFocus = "";\n            return;\n        }\n\n        pendingSemanticFocus = turnFocus.trim();\n        pendingSemanticAtMs = Math.max(System.currentTimeMillis(), answerUpdatedAtMs + 1L);\n        scheduleSemanticFallbackIfNeeded();\n    }\n\n    @Override\n    public void onStatus(long sessionSerial, long workSerial, boolean replyWork, String status) {'''
service = replace_once(service, marker, insert, 'service turn callback')
service = replace_once(
    service,
    '''        aiStatus = simplifyStatus(raw);\n        renderStatus();\n        renderDebug();\n        if (isListeningStatus(raw)) scheduleSemanticFallbackIfNeeded();''',
    '''        aiStatus = simplifyStatus(raw);\n        renderStatus();\n        renderDebug();''',
    'remove status semantic scheduling')
service = replace_once(
    service,
    '''    private static boolean isListeningStatus(String status) {\n        String lower = status == null ? "" : status.toLowerCase(Locale.ROOT).trim();\n        return lower.equals("слушам") || lower.equals("слушам…") || lower.contains("продължавам да слушам");\n    }\n\n''',
    '',
    'remove obsolete listening helper')
service_path.write_text(service)


policy_path = Path('app/src/main/java/com/livecopilot/micprobe/FileTurnReplyPolicy.java')
policy = policy_path.read_text()
policy = replace_once(
    policy,
    '''    static boolean canFinalize(long endSerial, long activeTurnSerial) {\n        return endSerial > 0L && endSerial == activeTurnSerial;\n    }\n\n''',
    '''    static boolean canFinalize(long endSerial, long activeTurnSerial) {\n        return endSerial > 0L && endSerial == activeTurnSerial;\n    }\n\n    static boolean shouldUseSemanticFallback(boolean mainReplyQueued, String turnFocus) {\n        return !mainReplyQueued && !clean(turnFocus).isEmpty();\n    }\n\n''',
    'semantic fallback policy')
policy_path.write_text(policy)


test_path = Path('app/src/test/java/com/livecopilot/micprobe/FileTurnReplyPolicyTest.java')
test = test_path.read_text()
test = replace_once(
    test,
    '''    @Test\n    public void focusAccumulatesAcrossForcedChunks() {''',
    '''    @Test\n    public void semanticFallbackRunsOnlyAfterTurnWithoutMainReply() {\n        assertTrue(FileTurnReplyPolicy.shouldUseSemanticFallback(false, "цялата реплика"));\n        assertFalse(FileTurnReplyPolicy.shouldUseSemanticFallback(true, "цялата реплика"));\n        assertFalse(FileTurnReplyPolicy.shouldUseSemanticFallback(false, "   "));\n    }\n\n    @Test\n    public void focusAccumulatesAcrossForcedChunks() {''',
    'semantic fallback tests')
test_path.write_text(test)
