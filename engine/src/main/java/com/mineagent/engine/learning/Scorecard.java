package com.mineagent.engine.learning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How well each way of doing something worked, rated by code alone (SMH plan 69, block 2; the user,
 * 2026-11-02: "The model should be able to test new workflows, and strategies ... With the effectiveness
 * of any task rated by hard code."). The model proposes ways of doing things (skills, plans, tactics); the
 * game carries them out; this rates each episode from game facts only, never from what the model says.
 *
 * <p>Two measures per strategy key ({@code skill:<name>}, {@code goal:<text>}, {@code fight:<tactic>}):
 * <ul>
 *   <li><b>Reliability</b>, after the Andy Omega plan (§41): the Wilson lower bound at 95% of its success
 *       rate, and tiers on it: candidate (10 trials, lb 0.45, 3 distinct conditions), certified (30, 0.78, 5),
 *       preferred (60, 0.88, 7). A skill that has never failed has not been tested hard enough to know how
 *       it fails, so the tiers are reachable with a failure or two.</li>
 *   <li><b>Efficiency</b>: a score of 0..100 per episode: done (acceptance checks passed, a run succeeded, a
 *       fight won) 60, up to 25 for speed against its own fastest verified run, up to 15 for health kept;
 *       not done up to 15; a fight survived by retreating 20 plus up to 15; a death 0.</li>
 * </ul>
 * Learning progress (§43.2) is the change in reliability between the last 10 trials and the 10 before:
 * improving, mastered, stuck (low and flat: the approach is wrong, not the effort) or regressing. Rot
 * (§41.4): a certified strategy whose last 20 trials fall more than 0.15 below its certified bound is
 * demoted, and the conditions its failures share are named ("all failures in swamp"). Failures are data.
 * {@link #rank} puts proven reliability first, then efficiency, and gives the little-tried a trial (UCB1).
 */
public final class Scorecard {
    public enum Outcome { DONE, FAILED, RETREATED }

    public enum Tier { NEW, CANDIDATE, CERTIFIED, PREFERRED }

    public enum Trend { UNTESTED, IMPROVING, MASTERED, STUCK, REGRESSING, STEADY }

    /**
     * One episode as the game saw it. Health in health points (20 is full). {@code conditions}: what it ran
     * under, as {@code name=value} pairs joined by commas (biome, dimension, tool, day or night).
     */
    public record Episode(String key, Outcome outcome, boolean died, long ticks, double damageTaken, double maxHealth,
                          String conditions) {
        public Episode(String key, Outcome outcome, boolean died, long ticks, double damageTaken, double maxHealth) {
            this(key, outcome, died, ticks, damageTaken, maxHealth, "");
        }

        public boolean succeeded() {
            return outcome == Outcome.DONE && !died;
        }
    }

    /**
     * What is kept per key. {@code recent}: the last outcomes, 1 or 0, oldest first. {@code conditionsDone} and
     * {@code conditionsFailed}: how often each condition was seen in a success and in a failure.
     */
    public record Stat(String key, int runs, double sum, double best, double last, long fastestTicks, long updated,
                       int successes, List<Integer> recent, Map<String, Integer> conditionsDone,
                       Map<String, Integer> conditionsFailed, boolean demoted, double certifiedLb) {
        public double mean() {
            return runs == 0 ? 0.0 : sum / runs;
        }

        public double lowerBound() {
            return wilson(successes, runs);
        }

        /** How many different situations (whole condition sets) it succeeded in. */
        public int distinctConditions() {
            if (conditionsDone == null) return 0;
            int n = 0;
            for (String k : conditionsDone.keySet()) if (k.startsWith(SET)) n++;
            return n;
        }

        List<Integer> recentOrEmpty() {
            return recent == null ? List.of() : recent;
        }

        Stat withDemotion(boolean d) {
            return d == demoted ? this : new Stat(key, runs, sum, best, last, fastestTicks, updated, successes, recent,
                    conditionsDone, conditionsFailed, d, certifiedLb);
        }
    }

    public record State(List<Stat> stats) {}

    private static final double EXPLORE = 0.35;
    private static final int MAX_KEYS = 4096;
    private static final int WINDOW = 20;
    private static final int HALF = 10;
    private static final double ROT_DELTA = 0.15;
    private static final int MAX_CONDITIONS = 96;
    /** A whole condition set, kept beside its parts: "set:" + the conditions as recorded. */
    private static final String SET = "set:";

    private final Map<String, Stat> stats = new ConcurrentHashMap<>();

    /** The Wilson score interval's lower bound at 95%. */
    public static double wilson(int successes, int n) {
        if (n <= 0) return 0.0;
        double z = 1.96;
        double p = (double) successes / n;
        double z2 = z * z;
        double centre = p + z2 / (2.0 * n);
        double margin = z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n));
        return (centre - margin) / (1 + z2 / n);
    }

    /** The efficiency score of an episode, against the fastest verified run seen for its key so far. */
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

    /** Rate an episode and keep it; returns its efficiency score. */
    public double record(Episode e, long now) {
        if (e == null || e.key() == null || e.key().isBlank()) return 0.0;
        double score = score(e);
        boolean ok = e.succeeded();
        String key = normal(e.key());
        stats.compute(key, (k, old) -> {
            long fastest = old == null ? 0 : old.fastestTicks();
            if (ok && e.ticks() > 0 && (fastest == 0 || e.ticks() < fastest)) fastest = e.ticks();
            List<Integer> recent = new ArrayList<>(old == null ? List.of() : old.recentOrEmpty());
            recent.add(ok ? 1 : 0);
            while (recent.size() > WINDOW) recent.remove(0);
            Map<String, Integer> done = new LinkedHashMap<>(old == null || old.conditionsDone() == null ? Map.of() : old.conditionsDone());
            Map<String, Integer> failed = new LinkedHashMap<>(old == null || old.conditionsFailed() == null ? Map.of() : old.conditionsFailed());
            List<String> parts = split(e.conditions());
            List<String> seen = new ArrayList<>(parts);
            if (!parts.isEmpty()) seen.add(SET + String.join(",", parts.stream().sorted().toList()));
            Map<String, Integer> into = ok ? done : failed;
            for (String c : seen) {
                if (into.size() < MAX_CONDITIONS || into.containsKey(c)) into.merge(c, 1, Integer::sum);
            }
            int runs = old == null ? 1 : old.runs() + 1;
            int successes = (old == null ? 0 : old.successes()) + (ok ? 1 : 0);
            Stat next = new Stat(k, runs, (old == null ? 0 : old.sum()) + score, Math.max(old == null ? 0 : old.best(), score),
                    score, fastest, now, successes, recent, done, failed, old != null && old.demoted(),
                    old == null ? 0.0 : old.certifiedLb());
            // The bound it had when certified is what rot is measured against (Andy Omega §41.4).
            if (earned(next).compareTo(Tier.CERTIFIED) >= 0 && next.lowerBound() > next.certifiedLb()) {
                next = new Stat(k, next.runs(), next.sum(), next.best(), next.last(), next.fastestTicks(), next.updated(),
                        next.successes(), next.recent(), next.conditionsDone(), next.conditionsFailed(), next.demoted(),
                        next.lowerBound());
            }
            return next.withDemotion(rotting(next));
        });
        if (stats.size() > MAX_KEYS) dropOldest();
        return score;
    }

    public Stat stat(String key) {
        return key == null ? null : stats.get(normal(key));
    }

    /** The tier a key has earned (a demoted one counts as a candidate at most until it recovers). */
    public Tier tier(String key) {
        Stat s = stat(key);
        if (s == null) return Tier.NEW;
        Tier earned = earned(s);
        return s.demoted() && earned.compareTo(Tier.CANDIDATE) > 0 ? Tier.CANDIDATE : earned;
    }

    private static Tier earned(Stat s) {
        double lb = s.lowerBound();
        int n = s.runs();
        int c = s.distinctConditions();
        if (n >= 60 && lb >= 0.88 && c >= 7) return Tier.PREFERRED;
        if (n >= 30 && lb >= 0.78 && c >= 5) return Tier.CERTIFIED;
        if (n >= 10 && lb >= 0.45 && c >= 3) return Tier.CANDIDATE;
        return Tier.NEW;
    }

    /** Reliability over the last 10 trials minus over the 10 before (needs 20 trials). */
    public double learningProgress(String key) {
        Stat s = stat(key);
        if (s == null || s.recentOrEmpty().size() < WINDOW) return 0.0;
        List<Integer> r = s.recentOrEmpty();
        return wilson(sum(r.subList(HALF, WINDOW)), HALF) - wilson(sum(r.subList(0, HALF)), HALF);
    }

    /** What the curriculum should do with a key (§43.2). */
    public Trend trend(String key) {
        Stat s = stat(key);
        if (s == null || s.runs() == 0) return Trend.UNTESTED;
        if (s.recentOrEmpty().size() < WINDOW) return s.runs() >= HALF && s.lowerBound() < 0.3 ? Trend.STUCK : Trend.IMPROVING;
        double lp = learningProgress(key);
        double recentLb = wilson(sum(s.recentOrEmpty()), s.recentOrEmpty().size());
        if (lp > 0.05) return Trend.IMPROVING;
        if (lp < -0.15) return Trend.REGRESSING;
        if (recentLb >= 0.78) return Trend.MASTERED;
        if (recentLb < 0.45) return Trend.STUCK;
        return Trend.STEADY;
    }

    /** Whether a once-certified key's last 20 trials fell more than 0.15 below the bound it was certified at. */
    private static boolean rotting(Stat s) {
        if (s.certifiedLb() <= 0.0) return false;
        List<Integer> r = s.recentOrEmpty();
        if (r.size() < WINDOW) return s.demoted();
        double rolling = (double) sum(r) / r.size();
        return rolling < s.certifiedLb() - ROT_DELTA;
    }

    /**
     * Why a key fails, from the conditions: the condition seen most in its failures that its successes rarely
     * had ("biome=swamp"), or null when nothing stands out.
     */
    public String failureCondition(String key) {
        Stat s = stat(key);
        if (s == null || s.conditionsFailed() == null || s.conditionsFailed().isEmpty()) return null;
        int fails = s.runs() - s.successes();
        if (fails < 3) return null;
        String worst = null;
        double gap = 0.5;
        for (var entry : s.conditionsFailed().entrySet()) {
            if (entry.getKey().startsWith(SET)) continue;
            double inFailures = (double) entry.getValue() / fails;
            int doneWith = s.conditionsDone() == null ? 0 : s.conditionsDone().getOrDefault(entry.getKey(), 0);
            double inSuccesses = s.successes() == 0 ? 0.0 : (double) doneWith / s.successes();
            if (inFailures - inSuccesses > gap) {
                gap = inFailures - inSuccesses;
                worst = entry.getKey();
            }
        }
        return worst;
    }

    /**
     * The candidates, best first: proven reliability, then efficiency, then a trial bonus for the little-tried
     * (UCB1). A key never run comes first: it has to be tried once to be rated.
     */
    public List<String> rank(Collection<String> keys) {
        int total = 0;
        for (String k : keys) {
            Stat s = stat(k);
            if (s != null) total += s.runs();
        }
        final int totalRuns = total;
        List<String> out = new ArrayList<>(keys);
        out.sort(Comparator.comparingDouble((String k) -> -choiceValue(k, totalRuns)));
        return out;
    }

    /** A key's value for choosing: 0..1 plus its trial bonus; 2 for one never tried. */
    public double choiceValue(String key, int totalRuns) {
        Stat s = stat(key);
        if (s == null || s.runs() == 0) return 2.0;
        double proven = s.demoted() ? s.lowerBound() * 0.5 : s.lowerBound();
        return 0.6 * proven + 0.4 * s.mean() / 100.0
                + EXPLORE * Math.sqrt(2.0 * Math.log(Math.max(1, totalRuns)) / s.runs());
    }

    /** The skills with the lowest reliability (rated at least once), as "name (score N)": worth practising. */
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

    /** The named skills by what the curriculum should do with them (§43.2), each list at most {@code limit}. */
    public Map<Trend, List<String>> byTrend(Collection<String> skillNames, int limit) {
        Map<Trend, List<String>> out = new HashMap<>();
        for (String name : skillNames) {
            Trend t = trend("skill:" + name);
            List<String> list = out.computeIfAbsent(t, x -> new ArrayList<>());
            if (list.size() < limit) list.add(name);
        }
        return out;
    }

    /** In a line for the model and the panel: the hard-coded rating, never the model's own. */
    public String describe(String key) {
        Stat s = stat(key);
        if (s == null) return "not rated yet";
        String why = s.demoted() ? failureCondition(key) : null;
        return String.format(Locale.ROOT, "%s, %d/%d done (reliability %.2f), score %.0f/100%s%s",
                tier(key).name().toLowerCase(Locale.ROOT), s.successes(), s.runs(), s.lowerBound(), s.mean(),
                s.demoted() ? ", failing lately" : "", why == null ? "" : " (failures share " + why + ")");
    }

    /**
     * The fight record, as a person reads it (combat learning, plan 69 block 3): for each mob fought most, the
     * stance the game has rated best against it, with its wins and tier. Keys are fight:<mob>:<stance>.
     */
    public List<String> fightRecord(int limit) {
        Map<String, List<Stat>> byMob = new java.util.HashMap<>();
        for (Stat s : stats.values()) {
            String[] parts = s.key().split(":");
            if (parts.length == 3 && parts[0].equals("fight")) byMob.computeIfAbsent(parts[1], k -> new ArrayList<>()).add(s);
        }
        List<Map.Entry<String, List<Stat>>> mobs = new ArrayList<>(byMob.entrySet());
        mobs.sort(java.util.Comparator.comparingInt((Map.Entry<String, List<Stat>> e) ->
                -e.getValue().stream().mapToInt(Stat::runs).sum()).thenComparing(Map.Entry::getKey));
        List<String> out = new ArrayList<>();
        for (var e : mobs) {
            if (out.size() >= limit) break;
            Stat best = e.getValue().stream().max(java.util.Comparator.comparingDouble(Stat::lowerBound)
                    .thenComparingDouble(Stat::mean)).orElseThrow();
            int fights = e.getValue().stream().mapToInt(Stat::runs).sum();
            String stance = best.key().substring(best.key().lastIndexOf(':') + 1);
            out.add(String.format(Locale.ROOT, "%s: %s works best, won %d of %d (%s); %d fights in all",
                    e.getKey().replace('_', ' '), stance, best.successes(), best.runs(),
                    tier(best.key()).name().toLowerCase(Locale.ROOT), fights));
        }
        return out;
    }

    public State exportState() {
        return new State(new ArrayList<>(stats.values()));
    }

    public void importState(State state) {
        stats.clear();
        if (state == null || state.stats() == null) return;
        for (Stat s : state.stats()) {
            if (s == null || s.key() == null || s.runs() < 0 || !Double.isFinite(s.sum())) continue;
            int successes = Math.max(0, Math.min(s.runs(), s.successes()));
            stats.put(normal(s.key()), new Stat(normal(s.key()), s.runs(), s.sum(), s.best(), s.last(), s.fastestTicks(),
                    s.updated(), successes, s.recent() == null ? List.of() : List.copyOf(s.recent()),
                    s.conditionsDone() == null ? Map.of() : Map.copyOf(s.conditionsDone()),
                    s.conditionsFailed() == null ? Map.of() : Map.copyOf(s.conditionsFailed()), s.demoted(),
                    Double.isFinite(s.certifiedLb()) ? s.certifiedLb() : 0.0));
        }
    }

    private void dropOldest() {
        stats.values().stream().min(Comparator.comparingLong(Stat::updated)).ifPresent(s -> stats.remove(s.key()));
    }

    /** Keys compare without case or extra spaces. */
    public static String normal(String key) {
        return key.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static List<String> split(String conditions) {
        List<String> out = new ArrayList<>();
        if (conditions == null) return out;
        for (String part : conditions.split(",")) {
            String p = part.strip().toLowerCase(Locale.ROOT);
            if (!p.isEmpty() && p.length() <= 64) out.add(p);
        }
        return out;
    }

    private static int sum(List<Integer> xs) {
        int s = 0;
        for (Integer x : xs) s += x == null ? 0 : x;
        return s;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }
}
