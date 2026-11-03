package com.mineagent.engine.loop;

/**
 * Whether the owner is AFK (SMH plan 70, MA5.4): in the game but not at the keys. Two signs: the game's own idle timer
 * (the time of their last input, the one the server's idle kick uses) shows nothing for {@link #AFK_AFTER_MS}, or
 * they said so ("afk", "brb") and have done nothing since. Any input after that means they're back. Pure: the caller
 * passes the times, in milliseconds.
 */
public final class OwnerActivity {
    /** No input for this long counts as AFK. */
    public static final long AFK_AFTER_MS = 180_000L;
    /** Saying "afk" is itself an input; anything this long after it counts as a new one. */
    static final long OWN_WORDS_MS = 1_500L;

    public enum Change { NONE, WENT_AFK, CAME_BACK }

    private long saidAfkAt;
    private boolean afk;
    private long afkSince;
    private int fightsWon;
    /** Questions it had for them while they were away, said when they're back (at most {@link #HELD_MAX}). */
    private final java.util.List<String> held = new java.util.ArrayList<>();
    static final int HELD_MAX = 3;

    /** They said they're going AFK. */
    public synchronized void saidAfk(long now) {
        saidAfkAt = now;
    }

    /** They said they're back. */
    public synchronized void saidBack() {
        saidAfkAt = 0L;
    }

    /** Whether they're AFK, from the time of their last input. */
    public static boolean idle(long now, long lastInput, long saidAfkAt, long afkAfter) {
        if (saidAfkAt > 0L && lastInput <= saidAfkAt + OWN_WORDS_MS) return true;
        return lastInput > 0L && now - lastInput >= afkAfter;
    }

    /** Look again; what changed. A new input clears what they said. */
    public synchronized Change update(long now, long lastInput) {
        return update(now, lastInput, AFK_AFTER_MS);
    }

    /** As {@link #update(long, long)}, with no input for {@code afkAfter} milliseconds counting as AFK (a setting). */
    public synchronized Change update(long now, long lastInput, long afkAfter) {
        if (saidAfkAt > 0L && lastInput > saidAfkAt + OWN_WORDS_MS) saidAfkAt = 0L;
        boolean now_ = idle(now, lastInput, saidAfkAt, afkAfter);
        if (now_ == afk) return Change.NONE;
        afk = now_;
        if (afk) {
            afkSince = now;
            fightsWon = 0;
            held.clear();
            return Change.WENT_AFK;
        }
        return Change.CAME_BACK;
    }

    public synchronized boolean afk() {
        return afk;
    }

    /** A fight it won while they were away, for the line it says when they're back. */
    public synchronized void wonFight() {
        if (afk) fightsWon++;
    }

    /** A question for them while they're away: it waits until they're back. Whether it was held (they're AFK). */
    public synchronized boolean hold(String question) {
        if (!afk || question == null || question.isBlank()) return false;
        String q = question.strip();
        if (!held.contains(q)) {
            if (held.size() >= HELD_MAX) held.remove(0);
            held.add(q);
        }
        return true;
    }

    /** The questions waiting for them. */
    public synchronized java.util.List<String> held() {
        return java.util.List.copyOf(held);
    }

    /** The one line it says when they're back. */
    public synchronized String welcomeBack(long now) {
        return welcomeBack(now, java.util.List.of());
    }

    /**
     * The one line it says when they're back: how long, the fights it won, what else happened ({@code notes}, from its
     * journal: what it stored, found, finished; at most three), and the questions it held. The questions are then
     * asked, so they are not held any more.
     */
    public synchronized String welcomeBack(long now, java.util.List<String> notes) {
        long minutes = Math.max(0L, (now - afkSince) / 60_000L);
        String away = minutes <= 1 ? "a minute" : minutes + " minutes";
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (fightsWon > 0) parts.add("I fought off " + fightsWon + (fightsWon == 1 ? " mob" : " mobs"));
        if (notes != null) {
            for (String n : notes) {
                if (n != null && !n.isBlank() && parts.size() < 4) parts.add(n.strip().replaceAll("[.]+$", ""));
            }
        }
        StringBuilder line = new StringBuilder("Welcome back. ");
        if (parts.isEmpty()) line.append("All quiet for ").append(away).append('.');
        else line.append(away.substring(0, 1).toUpperCase()).append(away.substring(1)).append(": ")
                .append(String.join("; ", parts)).append('.');
        if (!held.isEmpty()) line.append(" I wanted to ask: ").append(String.join(" ", held));
        held.clear();
        return line.toString();
    }
}
