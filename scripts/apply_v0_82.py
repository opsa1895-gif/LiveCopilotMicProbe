from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def replace_method(text, marker, replacement):
    start = text.find(marker)
    if start < 0:
        raise SystemExit(f"method marker not found: {marker}")
    brace = text.find('{', start)
    if brace < 0:
        raise SystemExit(f"opening brace not found: {marker}")
    depth = 0
    end = None
    for i in range(brace, len(text)):
        ch = text[i]
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end is None:
        raise SystemExit(f"closing brace not found: {marker}")
    return text[:start] + replacement.rstrip() + text[end:]


policy_path = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java')
policy = policy_path.read_text()
policy = replace_once(
    policy,
    '    static final long ROUTE_LATENCY_SWITCH_LOW_CONFIDENCE_EXTRA_MARGIN_MS = 200L;\n',
    '    static final long ROUTE_LATENCY_SWITCH_LOW_CONFIDENCE_EXTRA_MARGIN_MS = 200L;\n'
    '    static final long ROUTE_FLAP_WINDOW_MS = 20_000L;\n'
    '    static final long ROUTE_FLAP_MARGIN_STEP_MS = 200L;\n'
    '    static final long ROUTE_FLAP_MAX_EXTRA_MARGIN_MS = 600L;\n'
    '    static final int ROUTE_FLAP_MAX_SCORE = 3;\n'
    '    static final int ROUTE_PERFORMANCE_ROUTE_UNKNOWN = -1;\n'
    '    static final int ROUTE_PERFORMANCE_ROUTE_REALTIME = 0;\n'
    '    static final int ROUTE_PERFORMANCE_ROUTE_FILE = 1;\n',
    'flap constants')

helper_marker = '    static long adaptiveRouteLatencySwitchGuardMs(\n'
helpers = '''    static int activeRouteFlapScore(int currentScore, long lastDecisionAtMs, long nowMs) {
        int safe = Math.max(0, Math.min(ROUTE_FLAP_MAX_SCORE, currentScore));
        if (safe <= 0) return 0;
        if (lastDecisionAtMs <= 0L || nowMs < lastDecisionAtMs
                || nowMs - lastDecisionAtMs > ROUTE_FLAP_WINDOW_MS) return 0;
        return safe;
    }

    static int nextRouteFlapScore(int currentScore, int previousRoute,
                                  long previousDecisionAtMs, int nextRoute, long nowMs) {
        int safe = Math.max(0, Math.min(ROUTE_FLAP_MAX_SCORE, currentScore));
        boolean previousRouteKnown = previousRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || previousRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean nextRouteKnown = nextRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || nextRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean previousFresh = previousDecisionAtMs > 0L
                && nowMs >= previousDecisionAtMs
                && nowMs - previousDecisionAtMs <= ROUTE_FLAP_WINDOW_MS;
        if (!nextRouteKnown) return activeRouteFlapScore(safe, previousDecisionAtMs, nowMs);
        if (!previousRouteKnown || !previousFresh) return 0;
        if (nextRoute != previousRoute) {
            return Math.min(ROUTE_FLAP_MAX_SCORE, safe + 1);
        }
        return Math.max(0, safe - 1);
    }

    static long routeFlapExtraMarginMs(int routeFlapScore) {
        int safe = Math.max(0, Math.min(ROUTE_FLAP_MAX_SCORE, routeFlapScore));
        return Math.min(ROUTE_FLAP_MAX_EXTRA_MARGIN_MS,
                safe * ROUTE_FLAP_MARGIN_STEP_MS);
    }

    static boolean isTrackableRouteDecisionReason(String reason) {
        return "file-faster".equals(reason)
                || "switch-guard".equals(reason)
                || "flap-damp".equals(reason)
                || "rt-margin".equals(reason)
                || "rt-ready".equals(reason);
    }

'''
if helper_marker not in policy:
    raise SystemExit('adaptive guard marker missing')
policy = policy.replace(helper_marker, helpers + helper_marker, 1)

policy = replace_method(policy, '    static long routeLatencySwitchMarginMs(', '''    static long routeLatencySwitchMarginMs(
            long realtimeJitterMs, int realtimeSamples, long realtimeSampleAtMs,
            long fileJitterMs, int fileSamples, long fileSampleAtMs, long nowMs) {
        return routeLatencySwitchMarginMs(
                realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileJitterMs, fileSamples, fileSampleAtMs, nowMs, 0);
    }

    static long routeLatencySwitchMarginMs(
            long realtimeJitterMs, int realtimeSamples, long realtimeSampleAtMs,
            long fileJitterMs, int fileSamples, long fileSampleAtMs, long nowMs,
            int routeFlapScore) {
        long baseMarginMs = routeLatencyPreferenceMarginMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
        if (baseMarginMs == Long.MAX_VALUE) return Long.MAX_VALUE;
        baseMarginMs += routeFlapExtraMarginMs(routeFlapScore);
        long switchGuardMs = adaptiveRouteLatencySwitchGuardMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
        boolean newerFileEvidence = fileSampleAtMs > realtimeSampleAtMs;
        boolean recentRealtimeEvidence = realtimeSampleAtMs > 0L
                && nowMs >= realtimeSampleAtMs
                && nowMs - realtimeSampleAtMs < switchGuardMs;
        if (!newerFileEvidence || !recentRealtimeEvidence) return baseMarginMs;
        return baseMarginMs + adaptiveRouteLatencySwitchExtraMarginMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
    }''')

policy = replace_method(policy, '    static boolean hasGuardedFileLatencyAdvantage(', '''    static boolean hasGuardedFileLatencyAdvantage(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        return hasGuardedFileLatencyAdvantage(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs, 0);
    }

    static boolean hasGuardedFileLatencyAdvantage(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs, int routeFlapScore) {
        long realtimeRiskMs = riskAdjustedRouteLatency(realtimeEstimateMs, realtimeJitterMs);
        long fileRiskMs = riskAdjustedRouteLatency(fileEstimateMs, fileJitterMs);
        if (realtimeRiskMs < 0L || fileRiskMs < 0L) return false;
        long requiredMarginMs = routeLatencySwitchMarginMs(
                realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileJitterMs, fileSamples, fileSampleAtMs, nowMs, routeFlapScore);
        if (requiredMarginMs == Long.MAX_VALUE) return false;
        return realtimeRiskMs - fileRiskMs >= requiredMarginMs;
    }''')

policy = replace_method(policy, '    static long realtimeProbeRemainingMs(', '''    static long realtimeProbeRemainingMs(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        return realtimeProbeRemainingMs(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs, 0);
    }

    static long realtimeProbeRemainingMs(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs, int routeFlapScore) {
        if (realtimeSamples < MIN_ROUTE_LATENCY_SAMPLES
                || fileSamples < MIN_ROUTE_LATENCY_SAMPLES) return 0L;
        if (!isRouteLatencyFresh(fileSampleAtMs, nowMs)
                || realtimeSampleAtMs <= 0L || nowMs < realtimeSampleAtMs) return 0L;
        if (!hasGuardedFileLatencyAdvantage(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs,
                routeFlapScore)) return 0L;
        long intervalMs = adaptiveRealtimeProbeIntervalMs(
                realtimeEstimateMs, realtimeJitterMs, fileEstimateMs, fileJitterMs);
        if (intervalMs <= 0L) return 0L;
        long elapsedMs = nowMs - realtimeSampleAtMs;
        return elapsedMs >= intervalMs ? 0L : intervalMs - elapsedMs;
    }''')

policy = replace_method(policy, '    static String routeLatencyDecisionReason(', '''    static String routeLatencyDecisionReason(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        return routeLatencyDecisionReason(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs, 0);
    }

    static String routeLatencyDecisionReason(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs, int routeFlapScore) {
        long riskGapMs = routeLatencyRiskGapMs(
                realtimeEstimateMs, realtimeJitterMs, fileEstimateMs, fileJitterMs);
        long rawBaseMarginMs = routeLatencyPreferenceMarginMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
        if (riskGapMs == Long.MIN_VALUE || rawBaseMarginMs == Long.MAX_VALUE) return "learning";
        if (!isRouteLatencyFresh(fileSampleAtMs, nowMs)) return "file-stale";
        if (realtimeSampleAtMs <= 0L || nowMs < realtimeSampleAtMs) return "rt-clock";

        long flapExtraMarginMs = routeFlapExtraMarginMs(routeFlapScore);
        long effectiveBaseMarginMs = rawBaseMarginMs + flapExtraMarginMs;
        long probeRemainingMs = realtimeProbeRemainingMs(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs,
                routeFlapScore);
        if (probeRemainingMs > 0L) return "file-faster";

        long requiredMarginMs = routeLatencySwitchMarginMs(
                realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileJitterMs, fileSamples, fileSampleAtMs, nowMs, routeFlapScore);
        long guardRemainingMs = routeLatencySwitchGuardRemainingMs(
                realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileJitterMs, fileSamples, fileSampleAtMs, nowMs);
        if (guardRemainingMs > 0L
                && riskGapMs >= effectiveBaseMarginMs
                && riskGapMs < requiredMarginMs) return "switch-guard";
        if (flapExtraMarginMs > 0L
                && riskGapMs >= rawBaseMarginMs
                && riskGapMs < effectiveBaseMarginMs) return "flap-damp";

        long intervalMs = adaptiveRealtimeProbeIntervalMs(
                realtimeEstimateMs, realtimeJitterMs, fileEstimateMs, fileJitterMs);
        long elapsedMs = nowMs - realtimeSampleAtMs;
        if (requiredMarginMs != Long.MAX_VALUE
                && riskGapMs >= requiredMarginMs
                && intervalMs > 0L
                && elapsedMs >= intervalMs) return "rt-probe";
        if (riskGapMs < effectiveBaseMarginMs) return "rt-margin";
        return "rt-ready";
    }''')

policy = replace_method(policy, '    static boolean shouldPreferFileForLatency(', '''    static boolean shouldPreferFileForLatency(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        return shouldPreferFileForLatency(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs, 0);
    }

    static boolean shouldPreferFileForLatency(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs, int routeFlapScore) {
        return realtimeProbeRemainingMs(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs,
                routeFlapScore) > 0L;
    }''')
policy_path.write_text(policy)

client_path = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java')
client = client_path.read_text()
client = replace_once(
    client,
    '    private String lastRouteDecisionLabel = "file";\n    private String lastRouteDecisionReason = "rt-unavailable";\n',
    '    private String lastRouteDecisionLabel = "file";\n'
    '    private String lastRouteDecisionReason = "rt-unavailable";\n'
    '    private int routeFlapScore;\n'
    '    private int lastPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n'
    '    private long lastPerformanceRouteDecisionAtMs;\n',
    'client flap fields')
client = replace_once(
    client,
    '        lastRouteDecisionLabel = "file";\n        lastRouteDecisionReason = "rt-unavailable";\n        transportBlockedUntilMs = 0L;\n',
    '        lastRouteDecisionLabel = "file";\n'
    '        lastRouteDecisionReason = "rt-unavailable";\n'
    '        routeFlapScore = 0;\n'
    '        lastPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n'
    '        lastPerformanceRouteDecisionAtMs = 0L;\n'
    '        transportBlockedUntilMs = 0L;\n',
    'client flap reset')

client = replace_method(client, '    synchronized boolean beginTurn(int sourceSampleRate)', '''    synchronized boolean beginTurn(int sourceSampleRate) {
        long now = System.currentTimeMillis();
        decayRoutingPenaltiesLocked(now);
        long blockedUntilMs = RealtimeRoutingPolicy.effectiveBlockedUntil(
                transportBlockedUntilMs, outcomeBlockedUntilMs);
        lastRouteDecisionLabel = "file";
        lastRouteDecisionReason = "rt-unavailable";
        if (!RealtimeRoutingPolicy.shouldUseRealtime(ready, socket != null, now, blockedUntilMs)) {
            if (ready && socket != null && blockedUntilMs > now) {
                lastRouteDecisionLabel = "file-hyst";
                lastRouteDecisionReason = "route-blocked";
            } else if (!ready) {
                lastRouteDecisionReason = "rt-not-ready";
            } else if (socket == null) {
                lastRouteDecisionReason = "rt-no-socket";
            }
            return false;
        }
        if (turnActive) {
            lastRouteDecisionReason = "rt-busy";
            return false;
        }
        if (awaitingCompletion) {
            lastRouteDecisionReason = "rt-awaiting";
            return false;
        }
        if (sourceSampleRate <= 0) {
            lastRouteDecisionReason = "bad-rate";
            return false;
        }

        routeFlapScore = RealtimeRoutingPolicy.activeRouteFlapScore(
                routeFlapScore, lastPerformanceRouteDecisionAtMs, now);
        String latencyReason = RealtimeRoutingPolicy.routeLatencyDecisionReason(
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                realtimeRouteLatencySamples, realtimeRouteLatencySampleAtMs,
                fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs, now, routeFlapScore);
        if (RealtimeRoutingPolicy.shouldPreferFileForLatency(
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                realtimeRouteLatencySamples, realtimeRouteLatencySampleAtMs,
                fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs, now, routeFlapScore)) {
            lastRouteDecisionLabel = "file-perf";
            lastRouteDecisionReason = latencyReason;
            notePerformanceRouteDecisionLocked(
                    RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, latencyReason, now);
            return false;
        }
        lastRouteDecisionLabel = "rt";
        lastRouteDecisionReason = latencyReason;
        notePerformanceRouteDecisionLocked(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, latencyReason, now);
        turnActive = true;
        activeTurnSerial = ++serial;
        sentSamples = 0;
        partial.setLength(0);
        activeBackup.clear();
        activeBackupSampleRate = sourceSampleRate;
        streamingPreprocessor.reset(sourceSampleRate);
        return true;
    }''')

client = replace_method(client, '    synchronized long performanceProbeRemainingMs()', '''    synchronized long performanceProbeRemainingMs() {
        long now = System.currentTimeMillis();
        int activeFlapScore = RealtimeRoutingPolicy.activeRouteFlapScore(
                routeFlapScore, lastPerformanceRouteDecisionAtMs, now);
        return RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                realtimeRouteLatencySamples, realtimeRouteLatencySampleAtMs,
                fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs, now, activeFlapScore);
    }''')

diagnostic_marker = '    synchronized String routeDecisionDiagnostics() {'
helper = '''    private void notePerformanceRouteDecisionLocked(int route, String reason, long nowMs) {
        if (!RealtimeRoutingPolicy.isTrackableRouteDecisionReason(reason)) return;
        routeFlapScore = RealtimeRoutingPolicy.nextRouteFlapScore(
                routeFlapScore, lastPerformanceRoute, lastPerformanceRouteDecisionAtMs,
                route, nowMs);
        lastPerformanceRoute = route;
        lastPerformanceRouteDecisionAtMs = nowMs;
    }

'''
if diagnostic_marker not in client:
    raise SystemExit('diagnostics marker missing')
client = client.replace(diagnostic_marker, helper + diagnostic_marker, 1)

client = replace_method(client, '    synchronized String routeDecisionDiagnostics()', '''    synchronized String routeDecisionDiagnostics() {
        long now = System.currentTimeMillis();
        int activeFlapScore = RealtimeRoutingPolicy.activeRouteFlapScore(
                routeFlapScore, lastPerformanceRouteDecisionAtMs, now);
        StringBuilder out = new StringBuilder("route why ").append(lastRouteDecisionReason)
                .append(" • n ").append(realtimeRouteLatencySamples)
                .append('/').append(fileRouteLatencySamples);
        if (activeFlapScore > 0) {
            out.append(" • flap×").append(activeFlapScore)
                    .append(" +").append(
                            RealtimeRoutingPolicy.routeFlapExtraMarginMs(activeFlapScore))
                    .append("ms");
        }
        if (realtimeRouteLatencySamples < RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES
                || fileRouteLatencySamples < RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES) {
            return out.toString();
        }

        long riskGapMs = RealtimeRoutingPolicy.routeLatencyRiskGapMs(
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs);
        long requiredMarginMs = RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                realtimeRouteLatencyJitterMs, realtimeRouteLatencySamples,
                realtimeRouteLatencySampleAtMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs, now, activeFlapScore);
        long guardDurationMs = RealtimeRoutingPolicy.adaptiveRouteLatencySwitchGuardMs(
                realtimeRouteLatencyJitterMs, realtimeRouteLatencySamples,
                fileRouteLatencyJitterMs, fileRouteLatencySamples);
        long guardRemainingMs = RealtimeRoutingPolicy.routeLatencySwitchGuardRemainingMs(
                realtimeRouteLatencyJitterMs, realtimeRouteLatencySamples,
                realtimeRouteLatencySampleAtMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs, now);
        if (riskGapMs != Long.MIN_VALUE) {
            out.append(" • gap ").append(riskGapMs).append("ms");
        }
        if (requiredMarginMs != Long.MAX_VALUE) {
            out.append(" / need ").append(requiredMarginMs).append("ms");
        }
        out.append(" • guard ");
        if (guardRemainingMs > 0L) {
            out.append(guardRemainingMs).append('/').append(guardDurationMs).append("ms");
        } else {
            out.append("off/").append(guardDurationMs).append("ms");
        }
        return out.toString();
    }''')
client_path.write_text(client)

gradle_path = Path('app/build.gradle.kts')
gradle = gradle_path.read_text()
gradle = replace_once(
    gradle,
    '        versionCode = 82\n        versionName = "0.81.0-route-observability"\n',
    '        versionCode = 83\n        versionName = "0.82.0-route-flap-damping"\n',
    'version bump')
gradle_path.write_text(gradle)

test_path = Path('app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingFlapDampingPolicyTest.java')
test_path.write_text('''package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingFlapDampingPolicyTest {
    @Test
    public void alternatingPerformanceRoutesRaiseFlapScore() {
        int score = RealtimeRoutingPolicy.nextRouteFlapScore(
                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN, 0L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 1_000L);
        assertEquals(0, score);

        score = RealtimeRoutingPolicy.nextRouteFlapScore(
                score, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 1_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L);
        assertEquals(1, score);
        score = RealtimeRoutingPolicy.nextRouteFlapScore(
                score, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 3_000L);
        assertEquals(2, score);
        score = RealtimeRoutingPolicy.nextRouteFlapScore(
                score, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 3_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 4_000L);
        assertEquals(RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE, score);
    }

    @Test
    public void stableRouteDecaysScoreAndIdleClearsIt() {
        assertEquals(2, RealtimeRoutingPolicy.nextRouteFlapScore(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 1_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 2_000L));
        assertEquals(0, RealtimeRoutingPolicy.activeRouteFlapScore(
                3, 1_000L, 22_000L));
    }

    @Test
    public void flapScoreRaisesFileSwitchMargin() {
        long realtimeSampleAt = 95_000L;
        long fileSampleAt = 99_500L;
        long now = 100_000L;

        assertEquals(700L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                100L, 4, realtimeSampleAt,
                100L, 4, fileSampleAt, now, 0));
        assertEquals(1_100L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                100L, 4, realtimeSampleAt,
                100L, 4, fileSampleAt, now, 2));
    }

    @Test
    public void borderlineFileAdvantageIsDampedAfterARecentFlip() {
        long realtimeSampleAt = 95_000L;
        long fileSampleAt = 99_500L;
        long now = 100_000L;

        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 4, realtimeSampleAt,
                1_600L, 100L, 4, fileSampleAt, now, 0));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 4, realtimeSampleAt,
                1_600L, 100L, 4, fileSampleAt, now, 1));
        assertEquals("flap-damp", RealtimeRoutingPolicy.routeLatencyDecisionReason(
                2_400L, 100L, 4, realtimeSampleAt,
                1_600L, 100L, 4, fileSampleAt, now, 1));
    }

    @Test
    public void strongFileAdvantageStillWinsAtMaximumDamping() {
        long realtimeSampleAt = 95_000L;
        long fileSampleAt = 99_500L;
        long now = 100_000L;

        assertEquals(600L, RealtimeRoutingPolicy.routeFlapExtraMarginMs(
                RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                3_400L, 100L, 4, realtimeSampleAt,
                1_600L, 100L, 4, fileSampleAt, now,
                RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE));
        assertEquals("file-faster", RealtimeRoutingPolicy.routeLatencyDecisionReason(
                3_400L, 100L, 4, realtimeSampleAt,
                1_600L, 100L, 4, fileSampleAt, now,
                RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE));
    }

    @Test
    public void intentionalProbeAndLearningDoNotCountAsPerformanceSwitches() {
        assertFalse(RealtimeRoutingPolicy.isTrackableRouteDecisionReason("rt-probe"));
        assertFalse(RealtimeRoutingPolicy.isTrackableRouteDecisionReason("learning"));
        assertTrue(RealtimeRoutingPolicy.isTrackableRouteDecisionReason("file-faster"));
        assertTrue(RealtimeRoutingPolicy.isTrackableRouteDecisionReason("flap-damp"));
    }
}
''')
