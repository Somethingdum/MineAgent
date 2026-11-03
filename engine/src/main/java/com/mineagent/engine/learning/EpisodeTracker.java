package com.mineagent.engine.learning;

/**
 * One episode under way, as the game shows it (SMH plan 69, block 2): when it began, the health lost on the
 * way (every drop counts; healing never cancels damage) and whether the companion died. Fed from the server
 * tick; ended into a {@link Scorecard.Episode}. Not thread-safe: the server thread alone uses it.
 */
public final class EpisodeTracker {
    private String key;
    private long startTick;
    private double lastHealth = Double.NaN;
    private double damage;
    private double maxHealth = 20.0;
    private boolean died;
    private String conditions = "";

    public void begin(String key, long tick, double health, double maxHealth) {
        begin(key, tick, health, maxHealth, "");
    }

    /** {@code conditions}: what it runs under (biome, dimension, tool, day or night), for the scorecard. */
    public void begin(String key, long tick, double health, double maxHealth, String conditions) {
        this.conditions = conditions == null ? "" : conditions;
        this.key = key;
        this.startTick = tick;
        this.lastHealth = health;
        this.damage = 0.0;
        this.maxHealth = maxHealth > 0 ? maxHealth : 20.0;
        this.died = false;
    }

    public boolean active() {
        return key != null;
    }

    public String key() {
        return key;
    }

    /** The companion's health now; a death is noted. */
    public void observe(double health, boolean dead) {
        if (key == null) return;
        if (!Double.isNaN(lastHealth) && health < lastHealth) damage += lastHealth - health;
        lastHealth = health;
        if (dead) died = true;
    }

    /** The episode ended now, with this outcome; the tracker is free again. */
    public Scorecard.Episode end(Scorecard.Outcome outcome, long tick) {
        if (key == null) return null;
        Scorecard.Episode e = new Scorecard.Episode(key, outcome, died, Math.max(0, tick - startTick), damage, maxHealth, conditions);
        key = null;
        lastHealth = Double.NaN;
        return e;
    }

    /** Forget the episode without scoring it (the owner changed the goal). */
    public void drop() {
        key = null;
        lastHealth = Double.NaN;
    }
}
