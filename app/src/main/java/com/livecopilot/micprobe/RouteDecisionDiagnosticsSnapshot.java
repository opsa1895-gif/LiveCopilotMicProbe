package com.livecopilot.micprobe;

final class RouteDecisionDiagnosticsSnapshot {
    private final String decisionReason;
    private final int realtimeSamples;
    private final int fileSamples;
    private final boolean releaseFresh;
    private final String releaseReason;
    private final String releaseOutcome;
    private final int releaseStableStreak;
    private final int confirmTurns;
    private final String releaseBreakdown;
    private final String routeHistory;
    private final int stableRouteStreak;
    private final int flapScore;
    private final long flapExtraMarginMs;
    private final boolean latencyReady;
    private final long riskGapMs;
    private final long requiredMarginMs;
    private final long guardRemainingMs;
    private final long guardDurationMs;

    RouteDecisionDiagnosticsSnapshot(
            String decisionReason,
            int realtimeSamples,
            int fileSamples,
            boolean releaseFresh,
            String releaseReason,
            String releaseOutcome,
            int releaseStableStreak,
            int confirmTurns,
            String releaseBreakdown,
            String routeHistory,
            int stableRouteStreak,
            int flapScore,
            long flapExtraMarginMs,
            boolean latencyReady,
            long riskGapMs,
            long requiredMarginMs,
            long guardRemainingMs,
            long guardDurationMs) {
        this.decisionReason = decisionReason;
        this.realtimeSamples = Math.max(0, realtimeSamples);
        this.fileSamples = Math.max(0, fileSamples);
        this.releaseFresh = releaseFresh;
        this.releaseReason = releaseReason;
        this.releaseOutcome = releaseOutcome;
        this.releaseStableStreak = Math.max(0, releaseStableStreak);
        this.confirmTurns = Math.max(1, confirmTurns);
        this.releaseBreakdown = releaseBreakdown;
        this.routeHistory = routeHistory;
        this.stableRouteStreak = Math.max(0, stableRouteStreak);
        this.flapScore = Math.max(0, flapScore);
        this.flapExtraMarginMs = Math.max(0L, flapExtraMarginMs);
        this.latencyReady = latencyReady;
        this.riskGapMs = riskGapMs;
        this.requiredMarginMs = requiredMarginMs;
        this.guardRemainingMs = Math.max(0L, guardRemainingMs);
        this.guardDurationMs = Math.max(0L, guardDurationMs);
    }

    String format() {
        StringBuilder out = new StringBuilder("route why=")
                .append(labelOrDash(decisionReason))
                .append(" n=").append(realtimeSamples).append('/').append(fileSamples);
        if (releaseFresh && isKnown(releaseReason)) {
            out.append(" • release=").append(releaseReason)
                    .append(':').append(labelOrDash(releaseOutcome));
            if ("pending".equals(releaseOutcome)) {
                out.append('×').append(releaseStableStreak).append('/').append(confirmTurns);
            }
        }
        if (releaseBreakdown != null && !releaseBreakdown.isEmpty()) {
            out.append(" • stats{").append(releaseBreakdown).append('}');
        }
        if (isKnown(routeHistory)) {
            out.append(" • hist=").append(routeHistory);
            if (stableRouteStreak > 0) {
                out.append(" stable×").append(stableRouteStreak).append('/').append(confirmTurns);
            }
        }
        if (flapScore > 0) {
            out.append(" • flap×").append(flapScore)
                    .append("(+").append(flapExtraMarginMs).append("ms)");
        }
        if (!latencyReady) return out.toString();

        out.append(" • lat");
        if (riskGapMs != Long.MIN_VALUE) {
            out.append(" gap=").append(riskGapMs).append("ms");
        }
        if (requiredMarginMs != Long.MAX_VALUE) {
            out.append(" need=").append(requiredMarginMs).append("ms");
        }
        out.append(" guard=");
        if (guardRemainingMs > 0L) {
            out.append(guardRemainingMs).append('/').append(guardDurationMs).append("ms");
        } else {
            out.append("off/").append(guardDurationMs).append("ms");
        }
        return out.toString();
    }

    private static boolean isKnown(String value) {
        return value != null && !value.isEmpty() && !"-".equals(value);
    }

    private static String labelOrDash(String value) {
        return isKnown(value) ? value : "-";
    }
}
