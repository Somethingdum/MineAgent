package com.mineagent.engine.llm;

import java.util.Set;

/**
 * The role of each model call, for SMH (SMH plan 69, MA1.6). With the stored model {@code smh/auto},
 * a call is sent with its role as its model id ({@code smh/act}, {@code smh/plan}, ...) and SMH picks
 * the model and the queue for it. Any other stored model is sent unchanged, so the mod works without SMH.
 */
public final class RoleRouting {
    /** The model id that asks SMH to route each call by its role. */
    public static final String AUTO = "smh/auto";

    /** Turns woken to plan: rolling replans, verified goals, the curriculum and the goal book. */
    private static final Set<String> PLAN_TRIGGERS = Set.of("rolling_replan", "goal_verified", "curriculum", "goal_book");

    private RoleRouting() {}

    /** The role of a turn by what woke it: {@code plan} for planning wakes, {@code act} for everything else. */
    public static String roleForTrigger(String trigger) {
        return trigger != null && PLAN_TRIGGERS.contains(trigger) ? "plan" : "act";
    }

    /** The model id to send: the role under SMH's auto model, else the stored model as it is. */
    public static String modelFor(String stored, String role) {
        return AUTO.equals(stored) ? "smh/" + role : stored;
    }
}
