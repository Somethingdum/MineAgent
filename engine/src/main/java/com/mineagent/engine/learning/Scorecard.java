package com.mineagent.engine.learning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How well each way of doing something worked, rated by code alone (SMH plan 69, block 2; the user,
 * 2026-11-02: "The model should be able to test new workflows, and strategies ... With the effectiveness
 * of any task rated by hard code."). The model proposes ways of doing things (skills, plans, tactics); the
 * game carries them out; this scores each episode from game facts only, never from what the model says.
 *
 * <p>Each strategy has a key ({@code skill:<name>}, {@code goal:<text>}, {@code fight:<tactic>}). Its runs,
 * mean, best, last and fastest verified time are kept in the permanent library, so the companion comes out
 * better in every world. {@link #rank} puts the best first and gives a strategy tried too little an
 * honest trial now and then (UCB1), so it keeps testing instead of settling on its first success.
 *
 * <p>The score of an episode, 0 to 100:
 * <ul>
 *   <li>done (the planner's acceptance checks passed, a skill run succeeded, a fight won): 60, plus up to 25
 *       for speed against the fastest verified run of the same key, plus up to 15 for health kept;</li>
 *   <li>not done: up to 15 for health kept, and nothing else;</li>
 *   <li>a fight survived by retreating: 20, plus up to 15 for health kept;</li>
 *   <li>a death: 0.</li>
 * </ul>
 */
public final class Scorecard {
    public enum Outcome { DONE, FAILED, RETREATED }

    /** One episode as the game saw it. Health in health points (20 is full). */
    public record Episode(String key, Outcome outcome, boolean died, long ticks, double damageTaken, double maxHealth) {}

    /** What is kept per key. */
    public record Stat(String key, int runs, double sum, double best, double last, long fastestTicks, long updated) {
        public double mean() {
            return runs == 0 ? 0.0 : sum / runs;
        }
    }

    public record State(List<Stat> stats) {}

    /** How much a little-tried strategy is favoured (UCB1's constant, on scores scaled to 0..1). */
    private static final double EXPLORE = 0.35;
    private static final int MAX_KEYS = 4096;

    private final Map<String, Stat> stats = new ConcurrentHashMap<>();

    /** The score of an episode, against the fastest verified run seen for its key so far. */
    public double score(Episode e) {
        if (e == null || e.died()) return 0.0;
        double max = e.maxHealth() > 0 ? e.maxHealth() : 20.0;
        double kept = 1.0 - Math.min(1.0, Math.max(0.0, e.damageTaken()) / max);
        return switch (e.outcome()) {
            case DONE -> {
                Stat s = stats.get(normal(e.key()));
                long fastest = s != null && s.fastestTicks() > 0 ? s.fastestTicks() : e.ticks();
                double speed = e.ticks() <= 0 ? 1.0 : Math.min(1.0, (double) fastest / e.ticks());
                yield clamp(60.0 + 25.0 * speed + 15.0 * kept);
            }
            case RETREATED -> clamp(20.0 + 15.0 * kept);
            case FAILED -> clamp(15.0 * kept);
        };
    }

    /** Score an episode and keep it; returns the score. */
    public double record(Episode e, long now) {
        if (e == null || e.key() == null || e.key().isBlank()) return 0.0;
        double score = score(e);
        String key = normal(e.key());
        stats.compute(key, (k, old) -> {
            long fastest = old == null ? 0 : old.fastestTicks();
            if (e.outcome() == Outcome.DONE && !e.died() && e.ticks() > 0 && (fastest == 0 || e.ticks() < fastest)) {
                fastest = e.ticks();
            }
            if (old == null) return new Stat(k, 1, score, score, score, fastest, now);
            return new Stat(k, old.runs() + 1, old.sum() + score, Math.max(old.best(), score), score, fastest, now);
        });
        if (stats.size() > MAX_KEYS) dropOldest();
        return score;
    }

    public Stat stat(String key) {
        return key == null ? null : stats.get(normal(key));
    }

    /**
     * The candidates, best first by mean score plus a trial bonus for the little-tried (UCB1). A key never
     * run comes first: it has to be tried once to be rated.
     */
    public List<String> rank(Collection<String> keys) {
        int total = 0;
        for (String k : keys) {
            Stat s = stat(k);
            if (s != null) total += s.runs();
        }
        final double logTotal = Math.log(Math.max(1, total));
        List<String> out = new ArrayList<>(keys);
        out.sort(Comparator.comparingDouble((String k) -> -value(stat(k), logTotal)));
        return out;
    }

    /** A key's value for choosing, 0..1 plus its trial bonus; 2 for one never tried. */
    public double choiceValue(String key, int totalRuns) {
        return value(stat(key), Math.log(Math.max(1, totalRuns)));
    }

    private static double value(Stat s, double logTotal) {
        if (s == null || s.runs() == 0) return 2.0;
        return s.mean() / 100.0 + EXPLORE * Math.sqrt(2.0 * logTotal / s.runs());
    }

    /** The skills with the lowest mean scores (rated at least once), as "name (score N)": worth practising. */
    public List<String> weakest(Collection<String> skillNames, int limit) {
        List<Stat> rated = new ArrayList<>();
        for (String name : skillNames) {
            Stat s = stat("skill:" + name);
            if (s != null && s.runs() > 0) rated.add(s);
        }
        rated.sort(Comparator.comparingDouble(Stat::mean));
        List<String> out = new ArrayList<>();
        for (Stat s : rated.subList(0, Math.min(limit, rated.size()))) {
            out.add(String.format(Locale.ROOT, "%s (score %.0f)", s.key().substring("skill:".length()), s.mean()));
        }
        return out;
    }

    /** In a line for the model: the hard-coded rating, never the model's own. */
    public String describe(String key) {
        Stat s = stat(key);
        if (s == null) return "not rated yet";
        return String.format(Locale.ROOT, "score %.0f/100 over %d run%s (best %.0f, last %.0f)",
                s.mean(), s.runs(), s.runs() == 1 ? "" : "s", s.best(), s.last());
    }

    public State exportState() {
        return new State(new ArrayList<>(stats.values()));
    }

    public void importState(State state) {
        stats.clear();
        if (state == null || state.stats() == null) return;
        for (Stat s : state.stats()) {
            if (s == null || s.key() == null || s.runs() < 0 || !Double.isFinite(s.sum())) continue;
            stats.put(normal(s.key()), s);
        }
    }

    private void dropOldest() {
        stats.values().stream().min(Comparator.comparingLong(Stat::updated)).ifPresent(s -> stats.remove(s.key()));
    }

    /** Keys compare without case or extra spaces. */
    public static String normal(String key) {
        return key.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }
}
