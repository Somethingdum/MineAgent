package com.mineagent.engine.settings;

import java.util.Locale;

/**
 * How freely the companion calls its model (SMH plan 69, MA2.5; the user, 2026-11-02: "The AI calls can be
 * frequent, given the new speed increase."). The loop's call-saving limits, as levels. Busy is the default.
 *
 * @param decisionCache reuse a cached decision instead of asking the model
 * @param toolRounds    tool rounds in one turn, at most
 * @param history       conversation messages kept, at most
 * @param recentKeep    recent messages kept whole when history is trimmed
 * @param outputScale   the reply budget after the first round, times today's 4,096 tokens
 */
public record CallRate(String id, boolean decisionCache, int toolRounds, int history, int recentKeep, double outputScale) {
    public static final CallRate CALM = new CallRate("calm", true, 10, 24, 12, 1.0);
    public static final CallRate NORMAL = new CallRate("normal", true, 14, 32, 16, 1.5);
    public static final CallRate BUSY = new CallRate("busy", false, 20, 40, 20, 2.0);

    public static CallRate of(String id) {
        String k = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "calm" -> CALM;
            case "normal" -> NORMAL;
            default -> BUSY;
        };
    }

    /** MA2.4: the experiment budget per subject for a level (Low 6, Normal 12, High 24; default High). */
    public static int experimentBudget(String level) {
        String k = level == null ? "" : level.strip().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "low" -> 6;
            case "normal" -> 12;
            default -> 24;
        };
    }
}
