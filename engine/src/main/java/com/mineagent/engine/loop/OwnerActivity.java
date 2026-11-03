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
        if (saidAfkAt > 0L && lastInput > saidAfkAt + OWN_WORDS_MS) saidAfkAt = 0L;
        boolean now_ = idle(now, lastInput, saidAfkAt, AFK_AFTER_MS);
        if (now_ == afk) return Change.NONE;
        afk = now_;
        if (afk) {
            afkSince = now;
            fightsWon = 0;
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

    /** The one line it says when they're back. */
    public synchronized String welcomeBack(long now) {
        long minutes = Math.max(0L, (now - afkSince) / 60_000L);
        String away = minutes <= 1 ? "a minute" : minutes + " minutes";
        if (fightsWon == 0) return "Welcome back. All quiet for " + away + ".";
        return "Welcome back. " + away.substring(0, 1).toUpperCase() + away.substring(1) + "; I fought off "
                + fightsWon + (fightsWon == 1 ? " mob" : " mobs") + " while you were away.";
    }
}
