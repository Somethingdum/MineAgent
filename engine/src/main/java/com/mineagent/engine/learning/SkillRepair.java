package com.mineagent.engine.learning;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineagent.api.llm.ChatMessage;
import com.mineagent.api.llm.LLMRequest;
import com.mineagent.engine.llm.RoleRouting;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Skills that mend themselves, measured (after PSN, MIT: failure localised to a step, a targeted repair, versions kept
 * and rolled back; see THIRD_PARTY_NOTICES.md). Every skill run rates its steps: those before the one it stopped at
 * as done, that one as failed (keys {@code step:<skill>#<n>}). A skill that keeps failing at one step gets that step,
 * and only that step, rewritten by the model, as a new version ({@code <skill>~r<n>}) beside the old. Both are offered
 * and rated by the game; the worse one is removed. A certified skill is never edited, only given a rival.
 */
public final class SkillRepair {
    /** A step is blamed after this many failures, at this share of its runs. */
    public static final int MIN_FAILURES = 3;
    public static final double BLAME_SHARE = 0.4;
    /** A version is judged after this many runs; a clear loser earlier. */
    public static final int TRIAL_RUNS = 10;
    public static final int DECIDE_RUNS = 30;
    /** Versions of one skill at most (the base and its repairs). */
    public static final int MAX_LINEAGE = 4;
    /** A repair may put this many steps in place of the one. */
    public static final int MAX_REPLACEMENT = 3;
    private static final String MARK = "~r";

    public enum Verdict { TRYING, PROMOTE, ROLL_BACK }

    private SkillRepair() {}

    /** The step ratings for one finished run: every step before {@code stoppedAt} done, that step failed. */
    public static List<Scorecard.Episode> stepEpisodes(String skill, int stoppedAt, int stepCount, boolean succeeded) {
        List<Scorecard.Episode> out = new ArrayList<>();
        if (skill == null || stepCount <= 0) return out;
        int done = succeeded ? stepCount : Math.max(0, Math.min(stoppedAt, stepCount));
        for (int i = 0; i < done; i++) out.add(step(skill, i, true));
        if (!succeeded && stoppedAt >= 0 && stoppedAt < stepCount) out.add(step(skill, stoppedAt, false));
        return out;
    }

    private static Scorecard.Episode step(String skill, int index, boolean ok) {
        return new Scorecard.Episode(stepKey(skill, index), ok ? Scorecard.Outcome.DONE : Scorecard.Outcome.FAILED,
                false, 0L, 0.0, 20.0);
    }

    public static String stepKey(String skill, int index) {
        return "step:" + skill + "#" + index;
    }

    /** The step that fails most, if one fails often enough to blame. */
    public static OptionalInt weakestStep(Scorecard card, String skill, int stepCount) {
        int worst = -1;
        int worstFailures = 0;
        for (int i = 0; i < stepCount; i++) {
            Scorecard.Stat s = card.stat(stepKey(skill, i));
            if (s == null) continue;
            int failures = s.runs() - s.successes();
            if (failures >= MIN_FAILURES && failures >= BLAME_SHARE * s.runs() && failures > worstFailures) {
                worst = i;
                worstFailures = failures;
            }
        }
        return worst < 0 ? OptionalInt.empty() : OptionalInt.of(worst);
    }

    /** Whether a skill may be repaired now: not a healthy certified one, and no version of it still on trial. */
    public static boolean mayRepair(Scorecard card, String skill, Collection<String> lineage) {
        Scorecard.Tier tier = card.tier("skill:" + skill);
        Scorecard.Stat stat = card.stat("skill:" + skill);
        boolean healthyCertified = tier.compareTo(Scorecard.Tier.CERTIFIED) >= 0 && (stat == null || !stat.demoted());
        if (healthyCertified || lineage.size() >= MAX_LINEAGE) return false;
        for (String other : lineage) {
            if (!other.equals(skill) && isVersion(other) && runs(card, other) < TRIAL_RUNS) return false;
        }
        return true;
    }

    public static String baseOf(String name) {
        if (name == null) return "";
        int at = name.lastIndexOf(MARK);
        return at > 0 && name.substring(at + MARK.length()).matches("\\d+") ? name.substring(0, at) : name;
    }

    public static boolean isVersion(String name) {
        return name != null && !baseOf(name).equals(name);
    }

    /** The next version's name: {@code base~r2}, {@code base~r3}, ... past any that exist. */
    public static String nextVersion(String base, Collection<String> existing) {
        int n = 2;
        while (existing.contains(base + MARK + n)) n++;
        return base + MARK + n;
    }

    /**
     * A version against the best of its rivals in the lineage: on trial until it has run enough, then kept (and the
     * rivals removed) if it is clearly better, removed if clearly worse; at {@link #DECIDE_RUNS} the better reliability
     * stays, the elder on a tie.
     */
    public static Verdict judge(Scorecard card, String version, Collection<String> rivals) {
        Scorecard.Stat v = card.stat("skill:" + version);
        if (v == null) return Verdict.TRYING;
        Scorecard.Stat best = null;
        for (String r : rivals) {
            if (r.equals(version)) continue;
            Scorecard.Stat s = card.stat("skill:" + r);
            if (s != null && (best == null || s.lowerBound() > best.lowerBound())) best = s;
        }
        int failures = v.runs() - v.successes();
        if (v.successes() == 0 && failures >= TRIAL_RUNS / 2) return Verdict.ROLL_BACK; // plainly broken
        if (v.runs() < TRIAL_RUNS) return Verdict.TRYING;
        if (best == null) return Verdict.PROMOTE;
        double lv = v.lowerBound(), lb = best.lowerBound();
        if (lv >= lb + 0.05 && v.mean() >= best.mean() - 5.0) return Verdict.PROMOTE;
        if (lv <= lb - 0.05 || v.mean() <= best.mean() - 15.0) return Verdict.ROLL_BACK;
        if (v.runs() >= DECIDE_RUNS) return lv > lb ? Verdict.PROMOTE : Verdict.ROLL_BACK;
        return Verdict.TRYING;
    }

    /** The one call for a repair: the skill's steps, the failing step, how it failed, and where. */
    public static LLMRequest request(String baseUrl, String apiKey, String storedModel, double temperature,
                                     String skill, String goal, String sequenceJson, int index,
                                     List<String> evidence, String failingWhen, Set<String> tools) {
        String system = "You repair one step of a Minecraft companion's learned skill. The skill is a list of tool calls "
                + "that worked before; one step now fails often. Rewrite only that step: replace it with 1 to "
                + MAX_REPLACEMENT + " tool calls that do its job more reliably (other arguments, a check first, a "
                + "different tool). Keep every other step. Answer with JSON only: {\"steps\":[{\"tool\":\"...\","
                + "\"args\":{...}}],\"why\":\"one sentence\"}. Tools you may use: " + String.join(", ", tools) + ".";
        JsonObject ask = new JsonObject();
        ask.addProperty("skill", skill);
        ask.addProperty("goal", clip(goal, 240));
        ask.add("steps", JsonParser.parseString(sequenceJson));
        ask.addProperty("failing_step", index + 1);
        JsonArray ev = new JsonArray();
        for (String e : evidence == null ? List.<String>of() : evidence) ev.add(clip(e, 200));
        ask.add("how_it_failed", ev);
        if (failingWhen != null && !failingWhen.isBlank()) ask.addProperty("failures_share", failingWhen);
        return new LLMRequest(baseUrl, apiKey, RoleRouting.modelFor(storedModel, "plan"),
                List.of(ChatMessage.system(system), ChatMessage.user(ask.toString())),
                List.of(), List.of(), temperature, 500, null, null, null, null, Duration.ofSeconds(120), false);
    }

    /**
     * The repaired sequence: the model's steps in place of step {@code index}, checked (tools allowed, arguments an
     * object, the step's own bindings kept); empty if the reply is unusable or changes nothing.
     */
    public static Optional<String> apply(String sequenceJson, int index, String reply, Set<String> tools) {
        if (reply == null || sequenceJson == null) return Optional.empty();
        JsonArray steps;
        try {
            JsonElement parsed = JsonParser.parseString(sequenceJson);
            if (!parsed.isJsonArray()) return Optional.empty();
            steps = parsed.getAsJsonArray();
        } catch (RuntimeException bad) {
            return Optional.empty();
        }
        if (index < 0 || index >= steps.size()) return Optional.empty();
        String text = reply.replaceAll("(?s)<think>.*?</think>", "");
        int open = text.indexOf('{'), close = text.lastIndexOf('}');
        if (open < 0 || close <= open) return Optional.empty();
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(text.substring(open, close + 1));
            if (!e.isJsonObject()) return Optional.empty();
            o = e.getAsJsonObject();
        } catch (RuntimeException bad) {
            return Optional.empty();
        }
        if (!o.has("steps") || !o.get("steps").isJsonArray()) return Optional.empty();
        JsonArray proposed = o.getAsJsonArray("steps");
        if (proposed.isEmpty() || proposed.size() > MAX_REPLACEMENT) return Optional.empty();
        JsonObject replaced = steps.get(index).isJsonObject() ? steps.get(index).getAsJsonObject() : new JsonObject();
        List<JsonObject> clean = new ArrayList<>();
        for (JsonElement p : proposed) {
            if (!p.isJsonObject()) return Optional.empty();
            JsonObject step = p.getAsJsonObject();
            if (!step.has("tool") || !step.get("tool").isJsonPrimitive()) return Optional.empty();
            String tool = step.get("tool").getAsString().strip().toLowerCase(Locale.ROOT);
            if (!tools.contains(tool)) return Optional.empty();
            if (step.has("args") && !step.get("args").isJsonObject()) return Optional.empty();
            JsonObject out = new JsonObject();
            out.addProperty("tool", tool);
            out.add("args", step.has("args") ? step.getAsJsonObject("args").deepCopy() : new JsonObject());
            clean.add(out);
        }
        if (replaced.has("bindings")) clean.get(0).add("bindings", replaced.get("bindings").deepCopy());
        JsonArray result = new JsonArray();
        for (int i = 0; i < steps.size(); i++) {
            if (i == index) clean.forEach(result::add);
            else result.add(steps.get(i).deepCopy());
        }
        if (result.size() > 24 || result.toString().equals(steps.toString())) return Optional.empty();
        return Optional.of(result.toString());
    }

    private static int runs(Scorecard card, String skill) {
        Scorecard.Stat s = card.stat("skill:" + skill);
        return s == null ? 0 : s.runs();
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("[\\r\\n]+", " ").strip();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
