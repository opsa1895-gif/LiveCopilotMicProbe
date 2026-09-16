from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


policy_path = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java')
policy = policy_path.read_text()
old_release = '''    static boolean shouldReleaseRouteFlapHistory(int stableRouteStreak) {
        return stableRouteStreak >= ROUTE_FLAP_STABLE_CONFIRM_TURNS;
    }
'''
new_release = old_release + '''
    static boolean shouldReleaseRouteFlapHistoryForRegimeChange(
            int routeFlapScore, int changedRoute, int outlierDirection, int outlierStreak,
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        if (routeFlapScore <= 0
                || !isRouteLatencyRegimeChange(outlierDirection, outlierStreak)) return false;
        boolean fileFavoringShift = changedRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                ? outlierDirection > 0
                : changedRoute == ROUTE_PERFORMANCE_ROUTE_FILE && outlierDirection < 0;
        if (!fileFavoringShift) return false;
        if (!isRouteLatencyFresh(realtimeSampleAtMs, nowMs)
                || !isRouteLatencyFresh(fileSampleAtMs, nowMs)) return false;
        return hasConfidentFileLatencyAdvantage(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples,
                fileEstimateMs, fileJitterMs, fileSamples);
    }
'''
policy = replace_once(policy, old_release, new_release, 'policy regime release helper')
policy_path.write_text(policy)

client_path = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java')
client = client_path.read_text()
old_note_tail = '''        if (RealtimeRoutingPolicy.shouldReleaseRouteFlapHistory(stablePerformanceRouteStreak)) {
            routeFlapScore = 0;
            previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
        }
    }

    synchronized String routeDecisionDiagnostics() {
'''
new_note_tail = '''        if (RealtimeRoutingPolicy.shouldReleaseRouteFlapHistory(stablePerformanceRouteStreak)) {
            clearRouteFlapHistoryLocked(false);
        }
    }

    private void maybeReleaseRouteFlapHistoryForLatencyRegimeLocked(
            int changedRoute, int outlierDirection, int outlierStreak, long nowMs) {
        int activeFlapScore = RealtimeRoutingPolicy.activeRouteFlapScore(
                routeFlapScore, lastPerformanceRouteDecisionAtMs, nowMs);
        if (!RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                activeFlapScore, changedRoute, outlierDirection, outlierStreak,
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                realtimeRouteLatencySamples, realtimeRouteLatencySampleAtMs,
                fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs, nowMs)) return;
        clearRouteFlapHistoryLocked(true);
    }

    private void clearRouteFlapHistoryLocked(boolean resetStableStreak) {
        routeFlapScore = 0;
        previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
        if (resetStableStreak) stablePerformanceRouteStreak = 0;
    }

    synchronized String routeDecisionDiagnostics() {
'''
client = replace_once(client, old_note_tail, new_note_tail, 'client flap release helpers')

old_rt_tail = '''            realtimeRouteLatencySampleAtMs = now;
            realtimeRouteLatencyOutlierDirection = nextDirection;
            realtimeRouteLatencyOutlierStreak = nextOutlierStreak;
        }

        routingQualityPenalty = RealtimeRoutingPolicy.nextOutcomePenalty(
'''
new_rt_tail = '''            realtimeRouteLatencySampleAtMs = now;
            realtimeRouteLatencyOutlierDirection = nextDirection;
            realtimeRouteLatencyOutlierStreak = nextOutlierStreak;
            maybeReleaseRouteFlapHistoryForLatencyRegimeLocked(
                    RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                    nextDirection, nextOutlierStreak, now);
        }

        routingQualityPenalty = RealtimeRoutingPolicy.nextOutcomePenalty(
'''
client = replace_once(client, old_rt_tail, new_rt_tail, 'realtime regime release hook')

old_file_tail = '''            fileRouteLatencySampleAtMs = now;
            fileRouteLatencyOutlierDirection = nextDirection;
            fileRouteLatencyOutlierStreak = nextOutlierStreak;
        } else if (!performanceEligible) {
'''
new_file_tail = '''            fileRouteLatencySampleAtMs = now;
            fileRouteLatencyOutlierDirection = nextDirection;
            fileRouteLatencyOutlierStreak = nextOutlierStreak;
            maybeReleaseRouteFlapHistoryForLatencyRegimeLocked(
                    RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                    nextDirection, nextOutlierStreak, now);
        } else if (!performanceEligible) {
'''
client = replace_once(client, old_file_tail, new_file_tail, 'file regime release hook')
client_path.write_text(client)

build_path = Path('app/build.gradle.kts')
build = build_path.read_text()
build = replace_once(
    build,
    '        versionCode = 85\n        versionName = "0.84.0-stable-route-release"\n',
    '        versionCode = 86\n        versionName = "0.85.0-regime-aware-flap-release"\n',
    'version bump')
build_path.write_text(build)

test_path = Path('app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingRegimeFlapReleasePolicyTest.java')
test_path.write_text('''package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingRegimeFlapReleasePolicyTest {
    private static final long NOW = 100_000L;

    @Test
    public void confirmedRealtimeSlowdownReleasesActiveFlapDamping() {
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 99_000L, NOW));
    }

    @Test
    public void confirmedFileSpeedupReleasesActiveFlapDamping() {
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                1, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_000L,
                1_600L, 100L, 4, 99_500L, NOW));
    }

    @Test
    public void oppositeRegimeDirectionKeepsDamping() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 99_000L, NOW));
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_000L,
                1_600L, 100L, 4, 99_500L, NOW));
    }

    @Test
    public void unconfirmedShiftOrNoActiveDampingDoesNotRelease() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, 1,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 99_000L, NOW));
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 99_000L, NOW));
    }

    @Test
    public void weakOrStaleFileAdvantageKeepsDamping() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_000L, 100L, 4, 99_500L,
                1_500L, 100L, 4, 99_000L, NOW));
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 79_999L, NOW));
    }

    @Test
    public void noisyEvidenceStillRequiresConfidenceAwareMargin() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                3_000L, 900L, 3, 99_500L,
                2_000L, 900L, 3, 99_000L, NOW));
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                3_500L, 900L, 3, 99_500L,
                2_000L, 900L, 3, 99_000L, NOW));
    }
}
''')
