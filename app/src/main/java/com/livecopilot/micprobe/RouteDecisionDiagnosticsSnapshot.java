package com.livecopilot.micprobe;

final class RouteDecisionDiagnosticsSnapshot {
    static final int RELEASE_STATS_CHAR_BUDGET = 96;
    static final int SNAPSHOT_CHAR_BUDGET = 224;

    private static final String STATS_SECTION_PREFIX = " • stats{";
    private static final String STATS_OMISSION_MARKER = "…more";
    private static final int STATS_MIN_VISIBLE_PREFIX_CHARS = 24;
    private static final int CORE_OVERFLOW_BUDGET = 52;
    private static final int RELEASE_OVERFLOW_BUDGET = 44;
    private static final int HISTORY_OVERFLOW_BUDGET = 28;
    private static final int FLAP_OVERFLOW_BUDGET = 20;
    private static final int LATENCY_OVERFLOW_BUDGET = 80;
    private static final int BASE_STATS_OVERFLOW_RESERVE = 15;

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
        String core = coreSection();
        String release = releaseSection();
        String history = historySection();
        String flap = flapSection();
        String latency = latencySection();
        boolean hasStats = hasReleaseBreakdown(releaseBreakdown);
        String omittedStats = hasStats ? omittedStatsSection(releaseBreakdown) : "";

        int nonStatsLength = core.length() + release.length() + history.length()
                + flap.length() + latency.length();
        int requiredStatsReserve = omittedStats.length();
        if (nonStatsLength > SNAPSHOT_CHAR_BUDGET - requiredStatsReserve) {
            return boundedNonStatsSnapshot(core, release, history, flap, latency, omittedStats);
        }

        String stats = statsSection(
                releaseBreakdown, SNAPSHOT_CHAR_BUDGET - nonStatsLength);
        return core + release + stats + history + flap + latency;
    }

    private String coreSection() {
        return new StringBuilder("route why=")
                .append(labelOrDash(decisionReason))
                .append(" n=").append(realtimeSamples).append('/').append(fileSamples)
                .toString();
    }

    private String releaseSection() {
        if (!releaseFresh || !isKnown(releaseReason)) return "";
        StringBuilder out = new StringBuilder(" • release=")
                .append(releaseReason)
                .append(':').append(labelOrDash(releaseOutcome));
        if ("pending".equals(releaseOutcome)) {
            out.append('×').append(releaseStableStreak).append('/').append(confirmTurns);
        }
        return out.toString();
    }

    private String historySection() {
        if (!isKnown(routeHistory)) return "";
        StringBuilder out = new StringBuilder(" • hist=").append(routeHistory);
        if (stableRouteStreak > 0) {
            out.append(" stable×").append(stableRouteStreak).append('/').append(confirmTurns);
        }
        return out.toString();
    }

    private String flapSection() {
        if (flapScore <= 0) return "";
        return new StringBuilder(" • flap×").append(flapScore)
                .append("(+").append(flapExtraMarginMs).append("ms)")
                .toString();
    }

    private String latencySection() {
        if (!latencyReady) return "";
        StringBuilder out = new StringBuilder(" • lat");
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

    private static String statsSection(String value, int sectionBudget) {
        if (!hasReleaseBreakdown(value)) return "";
        String omittedSection = omittedStatsSection(value);
        if (sectionBudget < omittedSection.length()) return "";

        int contentBudget = Math.min(
                RELEASE_STATS_CHAR_BUDGET,
                sectionBudget - STATS_SECTION_PREFIX.length() - 1);
        return STATS_SECTION_PREFIX + boundedStatsContent(value, contentBudget) + '}';
    }

    private static String boundedStatsContent(String value, int charBudget) {
        if (!hasReleaseBreakdown(value) || charBudget <= 0) return "";
        if (value.length() <= charBudget) return value;

        int totalTokens = diagnosticTokenCount(value);
        String widestMarker = omissionMarker(totalTokens);
        int suffixLength = widestMarker.length() + 1;
        if (charBudget < STATS_MIN_VISIBLE_PREFIX_CHARS + suffixLength) {
            return widestMarker;
        }

        int contentLimit = charBudget - suffixLength;
        int boundary = lastWhitespaceBoundary(value, contentLimit);
        if (boundary <= 0) return widestMarker;

        String visiblePrefix = value.substring(0, boundary);
        int visibleTokens = diagnosticTokenCount(visiblePrefix);
        int omittedTokens = Math.max(1, totalTokens - visibleTokens);
        return visiblePrefix + ' ' + omissionMarker(omittedTokens);
    }

    private static String omittedStatsSection(String value) {
        return STATS_SECTION_PREFIX + omissionMarker(diagnosticTokenCount(value)) + '}';
    }

    private static String omissionMarker(int omittedTokens) {
        return STATS_OMISSION_MARKER + '+' + Math.max(1, omittedTokens);
    }

    private static int diagnosticTokenCount(String value) {
        if (!hasReleaseBreakdown(value)) return 0;
        int count = 0;
        boolean inToken = false;
        for (int i = 0; i < value.length(); i++) {
            boolean whitespace = Character.isWhitespace(value.charAt(i));
            if (!whitespace && !inToken) {
                count++;
                inToken = true;
            } else if (whitespace) {
                inToken = false;
            }
        }
        return count;
    }

    private static int lastWhitespaceBoundary(String value, int atOrBefore) {
        for (int i = Math.min(atOrBefore, value.length() - 1); i >= 0; i--) {
            if (Character.isWhitespace(value.charAt(i))) return i;
        }
        return -1;
    }

    private static String boundedNonStatsSnapshot(
            String core, String release, String history, String flap, String latency,
            String omittedStats) {
        int coreBudget = CORE_OVERFLOW_BUDGET;
        int releaseBudget = RELEASE_OVERFLOW_BUDGET;
        int historyBudget = HISTORY_OVERFLOW_BUDGET;
        int flapBudget = FLAP_OVERFLOW_BUDGET;
        int latencyBudget = LATENCY_OVERFLOW_BUDGET;
        if (!omittedStats.isEmpty()) {
            coreBudget -= 4;
            releaseBudget -= 4;
            historyBudget -= 2;
            flapBudget -= 2;
            latencyBudget -= 3;
            coreBudget -= Math.max(0, omittedStats.length() - BASE_STATS_OVERFLOW_RESERVE);
        }

        String bounded = boundedSection(core, coreBudget, false)
                + boundedSection(release, releaseBudget, false)
                + omittedStats
                + boundedSection(history, historyBudget, false)
                + boundedSection(flap, flapBudget, false)
                + boundedSection(latency, latencyBudget, true);
        if (bounded.length() <= SNAPSHOT_CHAR_BUDGET) return bounded;
        return bounded.substring(0, SNAPSHOT_CHAR_BUDGET - 1) + '…';
    }

    private static String boundedSection(String value, int charBudget, boolean preserveTail) {
        if (value == null || value.isEmpty() || charBudget <= 0) return "";
        if (value.length() <= charBudget) return value;
        if (charBudget == 1) return "…";
        if (!preserveTail) {
            return value.substring(0, charBudget - 1) + '…';
        }

        int headBudget = Math.min(20, charBudget - 1);
        int tailBudget = charBudget - headBudget - 1;
        return value.substring(0, headBudget) + '…'
                + value.substring(value.length() - tailBudget);
    }

    private static boolean hasReleaseBreakdown(String value) {
        return value != null && !value.isEmpty();
    }

    private static boolean isKnown(String value) {
        return value != null && !value.isEmpty() && !"-".equals(value);
    }

    private static String labelOrDash(String value) {
        return isKnown(value) ? value : "-";
    }
}
