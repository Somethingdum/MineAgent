package com.mineagent.engine.learning;

import com.mineagent.api.llm.ChatMessage;
import com.mineagent.api.llm.LLMRequest;
import com.mineagent.engine.llm.RoleRouting;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What the companion practises when nobody needs it (SMH plan 69, MA2.1; after Voyager's automatic
 * curriculum, MineDojo/Voyager, MIT: one concrete next task at a time, just beyond what it can do).
 *
 * <p>When the companion is free for a minute with no order, no hostile near and health above its flee
 * line, one {@code smh/plan} call proposes the next task, given the situation, the skills it has, their
 * hard-coded scores (Scorecard: the weakest are worth practising), what it has done and what was too hard.
 * The task runs through the normal loop; the planner's acceptance checks decide whether it was done.
 * Done goes to the done list; failed three times, to the too-hard list. An order from the owner or a
 * trusted player ends it at once, uncounted. The lists are this world's (curriculum.json).
 */
public final class Curriculum {
    public static final long IDLE_MS = 60_000L;
    public static final long EVERY_MS = 30_000L;
    public static final int TOO_HARD_AFTER = 3;
    /** A practice task not verified within this many game ticks (5 minutes) counts as a failed attempt. */
    public static final long TASK_TICKS = 6_000L;
    private static final int KEEP = 64;

    public record State(List<String> done, Map<String, Integer> failures, List<String> tooHard) {}

    private final LinkedHashSet<String> done = new LinkedHashSet<>();
    private final Map<String, Integer> failures = new LinkedHashMap<>();
    private final LinkedHashSet<String> tooHard = new LinkedHashSet<>();
    private long lastProposalMs = Long.MIN_VALUE / 2;
    private String current;
    private long currentSinceTick;
    private boolean asking;

    /** Whether it is time to ask for a practice task. */
    public synchronized boolean due(boolean enabled, boolean busy, long msSinceOrder, boolean hostileNear,
                                    double health, double healthFlee, long nowMs) {
        return enabled && !busy && !asking && current == null && msSinceOrder >= IDLE_MS && !hostileNear
                && health > healthFlee && nowMs - lastProposalMs >= EVERY_MS;
    }

    /** A proposal is being asked for now (one at a time). */
    public synchronized void asking(long nowMs) {
        asking = true;
        lastProposalMs = nowMs;
    }

    public synchronized void answered() {
        asking = false;
    }

    /** The one call for a proposal: no tools, JSON {"task","why"}. */
    public static LLMRequest request(String baseUrl, String apiKey, String storedModel, double temperature,
                                     String situation, List<String> skills, List<String> weakest,
                                     List<String> opportunities, List<String> doneList, List<String> tooHardList,
                                     String longGoal) {
        StringBuilder sys = new StringBuilder()
                .append("You choose the next practice task for an AI companion in Minecraft, so it gets better at the game. ")
                .append("Pick ONE concrete task it can finish in a few minutes from where it is, just beyond what it already does well, ")
                .append("written like \"Mine 3 iron ore\", \"Craft a stone pickaxe\", \"Smelt 4 iron ingots\" or \"Kill 1 zombie\". ")
                .append("Prefer a skill that is improving, has a low score, or something it has never done. ")
                .append("A stuck skill needs a different way of doing it, not more of the same; never repeat a mastered one. ")
                .append("Never repeat a done task unless its score is low; never pick a task from the too-hard list. ")
                .append("Nothing that harms players, their builds or their pets. ")
                .append("Answer with JSON only: {\"task\":\"...\",\"why\":\"...\"}.");
        StringBuilder user = new StringBuilder();
        if (longGoal != null && !longGoal.isBlank()) user.append("Its long goal: ").append(clip(longGoal, 240)).append('\n');
        user.append(clip(situation, 1200)).append('\n');
        user.append("Skills it has: ").append(list(skills, 30)).append('\n');
        if (!weakest.isEmpty()) user.append("How its skills are going (measured by the game): ").append(list(weakest, 12)).append('\n');
        if (!opportunities.isEmpty()) user.append("Things it has not worked out yet: ").append(list(opportunities, 5)).append('\n');
        user.append("Done: ").append(list(doneList, 20)).append('\n');
        user.append("Too hard: ").append(list(tooHardList, 20));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(sys.toString()));
        messages.add(ChatMessage.user(user.toString()));
        return new LLMRequest(baseUrl, apiKey, RoleRouting.modelFor(storedModel, "plan"),
                messages, List.of(), List.of(), temperature, 300, null, null, null, null,
                Duration.ofSeconds(120), false);
    }

    /** The task in a reply, if it is a usable one: short, one line, not too hard. */
    public synchronized Optional<String> parse(String reply) {
        if (reply == null) return Optional.empty();
        String text = reply.replaceAll("(?s)<think>.*?</think>", "").strip();
        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        if (open < 0 || close <= open) return Optional.empty();
        String task;
        try {
            var obj = com.google.gson.JsonParser.parseString(text.substring(open, close + 1)).getAsJsonObject();
            task = obj.has("task") && obj.get("task").isJsonPrimitive() ? obj.get("task").getAsString() : "";
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
        task = task.replaceAll("[\\r\\n]+", " ").strip();
        if (task.length() < 3 || task.length() > 120 || tooHard.contains(norm(task))) return Optional.empty();
        return Optional.of(task);
    }

    /** The practice task now under way. */
    public synchronized void begin(String task, long tick) {
        current = task;
        currentSinceTick = tick;
    }

    public synchronized String current() {
        return current;
    }

    public synchronized boolean overdue(long tick) {
        return current != null && tick - currentSinceTick >= TASK_TICKS;
    }

    /** The task's checks passed. */
    public synchronized void verified() {
        if (current == null) return;
        String k = norm(current);
        done.remove(k);
        done.add(k);
        failures.remove(k);
        current = null;
        trim();
    }

    /** The task was not done in time, or the companion died: one failed attempt. */
    public synchronized void failed() {
        if (current == null) return;
        String k = norm(current);
        int n = failures.merge(k, 1, Integer::sum);
        if (n >= TOO_HARD_AFTER) {
            failures.remove(k);
            tooHard.add(k);
        }
        current = null;
        trim();
    }

    /** An order came: the task ends at once, uncounted. */
    public synchronized void abandon() {
        current = null;
    }

    public synchronized List<String> doneList() {
        return List.copyOf(done);
    }

    public synchronized List<String> tooHardList() {
        return List.copyOf(tooHard);
    }

    public synchronized State exportState() {
        return new State(List.copyOf(done), new LinkedHashMap<>(failures), List.copyOf(tooHard));
    }

    public synchronized void importState(State s) {
        done.clear();
        failures.clear();
        tooHard.clear();
        if (s == null) return;
        if (s.done() != null) for (String t : s.done()) if (t != null) done.add(norm(t));
        if (s.failures() != null) s.failures().forEach((k, v) -> { if (k != null && v != null && v > 0) failures.put(norm(k), v); });
        if (s.tooHard() != null) for (String t : s.tooHard()) if (t != null) tooHard.add(norm(t));
        trim();
    }

    private void trim() {
        while (done.size() > KEEP) done.remove(done.iterator().next());
        while (tooHard.size() > KEEP) tooHard.remove(tooHard.iterator().next());
        while (failures.size() > KEEP) failures.remove(failures.keySet().iterator().next());
    }

    private static String norm(String t) {
        return t.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String list(List<String> items, int max) {
        if (items == null || items.isEmpty()) return "none";
        return String.join(", ", items.subList(0, Math.min(max, items.size())));
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
