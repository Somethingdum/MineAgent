package com.mineagent.engine.combat;

import com.mineagent.engine.learning.Scorecard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Fighting ways the model came up with, kept and rated (combat learning): the model's advised tactics for a mob
 * become a named variant beside the four stances ({@code fight:<mob>:<name>} on the scorecard), are tried in later
 * fights, and are dropped when the game rates them worse than the best stance. Names say what differs, so the
 * fight record reads plainly ("balanced-r8-noshield"). Pure.
 */
public final class TacticBook {
    public record Variant(String mob, String name, Tactics tactics, long created) {}

    public record State(List<Variant> variants) {}

    /** The fight a choice makes: the scorecard name and the tactics. */
    public record Choice(String name, Tactics tactics) {}

    /** Variants kept per mob, besides the four stances. */
    public static final int MAX_PER_MOB = 4;
    /** A variant is judged after this many fights. */
    public static final int TRIAL_FIGHTS = 10;

    private final List<Variant> variants = new ArrayList<>();

    /** The name a set of tactics goes by: its stance, and what it does differently from that stance's own settings. */
    public static String nameOf(Tactics t, double fleeHealth) {
        Tactics base = Tactics.of(t.stance(), fleeHealth);
        StringBuilder b = new StringBuilder(t.stance());
        if (Math.round(t.retreatHealth()) != Math.round(base.retreatHealth())) b.append("-r").append(Math.round(t.retreatHealth()));
        if (t.useShield() != base.useShield()) b.append(t.useShield() ? "-shield" : "-noshield");
        if (t.crits() != base.crits()) b.append(t.crits() ? "-crits" : "-nocrits");
        if (!t.targetOrder().isEmpty()) b.append("-first-").append(t.targetOrder().get(0).replaceAll("[^a-z0-9_]", ""));
        return b.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Keep the model's tactics for {@code mob}; the name to rate them by. The same tactics as a stance's own are that
     * stance. When the mob's list is full, the worst-rated variant with five fights or more makes room; else the
     * tactics are used under the nearest stance's name.
     */
    public synchronized String adopt(String mob, Tactics t, double fleeHealth, Scorecard card, long now) {
        String name = nameOf(t, fleeHealth);
        if (name.equals(t.stance())) return name;
        if (find(mob, name).isPresent()) return name;
        List<Variant> mine = variants.stream().filter(v -> v.mob().equals(mob)).toList();
        if (mine.size() >= MAX_PER_MOB) {
            Variant worst = null;
            double worstLb = Double.MAX_VALUE;
            for (Variant v : mine) {
                Scorecard.Stat s = card.stat(key(mob, v.name()));
                if (s == null || s.runs() < 5) continue;
                if (s.lowerBound() < worstLb) {
                    worstLb = s.lowerBound();
                    worst = v;
                }
            }
            if (worst == null) return t.stance();
            variants.remove(worst);
        }
        variants.add(new Variant(mob, name, t, now));
        return name;
    }

    /** Every way it may fight this mob: the four stances, then the kept variants. */
    public synchronized List<String> names(String mob) {
        List<String> out = new ArrayList<>(CombatBrain.stances());
        for (Variant v : variants) if (v.mob().equals(mob)) out.add(v.name());
        return out;
    }

    /** The tactics for a name: a stance's own, or a kept variant's. */
    public synchronized Tactics tactics(String mob, String name, double fleeHealth) {
        return find(mob, name).map(Variant::tactics).orElseGet(() -> Tactics.of(name, fleeHealth));
    }

    /** The way the game has rated best against this mob (one never tried gets its trial first). */
    public synchronized Choice best(String mob, Scorecard card, double fleeHealth) {
        List<String> keys = new ArrayList<>();
        for (String n : names(mob)) keys.add(key(mob, n));
        String top = card.rank(keys).get(0);
        String name = top.substring(top.lastIndexOf(':') + 1);
        return new Choice(name, tactics(mob, name, fleeHealth));
    }

    /** Drop the variants that have had their trial and done worse than the best stance. The names dropped. */
    public synchronized List<String> prune(String mob, Scorecard card) {
        double bestStance = -1.0;
        for (String s : CombatBrain.stances()) {
            Scorecard.Stat st = card.stat(key(mob, s));
            if (st != null) bestStance = Math.max(bestStance, st.lowerBound());
        }
        List<String> dropped = new ArrayList<>();
        if (bestStance < 0) return dropped;
        for (Variant v : List.copyOf(variants)) {
            if (!v.mob().equals(mob)) continue;
            Scorecard.Stat st = card.stat(key(mob, v.name()));
            if (st != null && st.runs() >= TRIAL_FIGHTS && st.lowerBound() < bestStance - 0.05) {
                variants.remove(v);
                dropped.add(v.name());
            }
        }
        return dropped;
    }

    public static String key(String mob, String name) {
        return "fight:" + mob + ":" + name;
    }

    public synchronized State exportState() {
        return new State(List.copyOf(variants));
    }

    public synchronized void importState(State s) {
        variants.clear();
        if (s == null || s.variants() == null) return;
        for (Variant v : s.variants()) {
            if (v == null || v.mob() == null || v.name() == null || v.tactics() == null) continue;
            if (variants.stream().filter(x -> x.mob().equals(v.mob())).count() >= MAX_PER_MOB) continue;
            variants.add(v);
        }
    }

    private Optional<Variant> find(String mob, String name) {
        return variants.stream().filter(v -> v.mob().equals(mob) && v.name().equals(name)).findFirst();
    }
}
