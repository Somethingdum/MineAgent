package com.mineagent.engine.planning;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineagent.api.llm.ChatMessage;
import com.mineagent.api.llm.LLMRequest;
import com.mineagent.engine.llm.RoleRouting;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A second read of plans (SMH plan 69, MA4.1): a new plan of four steps or more, or a goal that has failed the same
 * way twice, gets one smh/review call in a fresh conversation (the same model, with nothing of the first one's
 * reasoning), which answers {"ok":bool,"fix":"..."}. A fix goes back to the planner as a replan. At most
 * {@link #PER_GOAL} reviews per goal, so a disagreement cannot loop.
 */
public final class PlanReview {
    public static final int MIN_STEPS = 4;
    public static final int FAILURES_BEFORE_REVIEW = 2;
    public static final int PER_GOAL = 3;
    public static final int MAX_FIX = 300;

    private final Map<String, Integer> failures = new HashMap<>();
    private final Map<String, Integer> reviews = new HashMap<>();

    /** A new plan was accepted: whether it gets a review. */
    public synchronized boolean onPlan(String goal, int steps) {
        return steps >= MIN_STEPS && take(goal);
    }

    /** A replan for repeated failure: whether this one (the second for the goal, and so on) gets a review. */
    public synchronized boolean onRepeatedFailure(String goal) {
        int n = failures.merge(key(goal), 1, Integer::sum);
        return n >= FAILURES_BEFORE_REVIEW && n % FAILURES_BEFORE_REVIEW == 0 && take(goal);
    }

    private boolean take(String goal) {
        String k = key(goal);
        int done = reviews.getOrDefault(k, 0);
        if (done >= PER_GOAL) return false;
        reviews.put(k, done + 1);
        if (reviews.size() > 256) reviews.clear();
        if (failures.size() > 256) failures.clear();
        return true;
    }

    private static String key(String goal) {
        return goal == null ? "" : goal.strip().toLowerCase(java.util.Locale.ROOT);
    }

    public static LLMRequest request(String baseUrl, String apiKey, String storedModel, double temperature,
                                     String goal, List<String> steps, String brief, String why) {
        String system = "You check a Minecraft companion's plan before it acts on it. Read the goal, the steps in order "
                + "and the situation. Look for a missing step (a tool or material needed before it is used, a place "
                + "to reach first), a step in the wrong order, or a step that cannot work. Answer with JSON only: "
                + "{\"ok\":true} if it will work, else {\"ok\":false,\"fix\":\"what to change, in one or two sentences\"}.";
        JsonObject ask = new JsonObject();
        ask.addProperty("goal", clip(goal, 300));
        JsonArray s = new JsonArray();
        for (String step : steps == null ? List.<String>of() : steps) s.add(clip(step, 200));
        ask.add("steps", s);
        if (brief != null) ask.addProperty("situation", clip(brief, 600));
        if (why != null) ask.addProperty("why_checked", why);
        return new LLMRequest(baseUrl, apiKey, RoleRouting.modelFor(storedModel, "review"),
                List.of(ChatMessage.system(system), ChatMessage.user(ask.toString())),
                List.of(), List.of(), temperature, 200, null, null, null, null, Duration.ofSeconds(120), false);
    }

    /** The fix, when the review says the plan will not work; empty when it is fine or the reply is unusable. */
    public static Optional<String> fix(String reply) {
        if (reply == null) return Optional.empty();
        String text = reply.replaceAll("(?s)<think>.*?</think>", "");
        int open = text.indexOf('{'), close = text.lastIndexOf('}');
        if (open < 0 || close <= open) return Optional.empty();
        try {
            JsonElement e = JsonParser.parseString(text.substring(open, close + 1));
            if (!e.isJsonObject()) return Optional.empty();
            JsonObject o = e.getAsJsonObject();
            boolean ok = !o.has("ok") || !o.get("ok").isJsonPrimitive() || o.get("ok").getAsBoolean();
            if (ok || !o.has("fix") || !o.get("fix").isJsonPrimitive()) return Optional.empty();
            String fix = clip(o.get("fix").getAsString(), MAX_FIX);
            return fix.isBlank() ? Optional.empty() : Optional.of(fix);
        } catch (RuntimeException bad) {
            return Optional.empty();
        }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("[\\r\\n]+", " ").strip();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
