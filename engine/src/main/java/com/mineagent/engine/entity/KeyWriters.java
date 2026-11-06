package com.mineagent.engine.entity;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * SMH plan 71, invariant I4 (seam B, §6.2 rule 2): one writer per key, per tick. Each write to a companion's keys
 * (walk, strafe, jump, sneak, sprint, the two clicks) is recorded with who made it, by the label its caller runs under
 * ({@link #as}: "automatone" for Automatone's command helper, "altoclef" for AltoClef's keys; anything else is the
 * planner's). A key written by two of them in one tick is a clash, kept for the scenes to read and counted. Written
 * for this mod.
 */
public final class KeyWriters {
    /** Who is writing on this thread now; the planner's, unless a caller says otherwise. */
    private static final ThreadLocal<String> WHO = ThreadLocal.withInitial(() -> "planner");

    /** Two writers of one key in one tick. */
    public record Clash(String body, long tick, String key, String first, String second) {
        @Override
        public String toString() {
            return body + " tick " + tick + ": " + key + " by " + first + " and " + second;
        }
    }

    /** Per body: per key, the tick and the writer of its last write. */
    private static final Map<UUID, Map<String, Object[]>> LAST = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Clash> CLASHES = new ConcurrentLinkedQueue<>();
    private static final int KEPT = 200;

    private KeyWriters() {}

    /** Runs {@code what} with its key writes counted as {@code who}'s. */
    public static <T> T as(String who, Supplier<T> what) {
        String before = WHO.get();
        WHO.set(who);
        try {
            return what.get();
        } finally {
            WHO.set(before);
        }
    }

    public static void as(String who, Runnable what) {
        as(who, () -> {
            what.run();
            return null;
        });
    }

    /** Who is writing on this thread now. */
    public static String who() {
        return WHO.get();
    }

    /** A write of {@code key} to this body's keys, by whoever is writing now. */
    static void wrote(ServerPlayer body, String key) {
        if (body == null || body.getServer() == null) return;
        long tick = body.getServer().getTickCount();
        String who = WHO.get();
        Object[] last = LAST.computeIfAbsent(body.getUUID(), k -> new ConcurrentHashMap<>()).put(key, new Object[] {tick, who});
        if (last != null && (long) last[0] == tick && !who.equals(last[1])) {
            CLASHES.add(new Clash(body.getGameProfile().getName(), tick, key, (String) last[1], who));
            while (CLASHES.size() > KEPT) CLASHES.poll();
        }
    }

    /** The clashes kept for this body (by its name), oldest first. */
    public static List<Clash> clashes(String body) {
        List<Clash> out = new ArrayList<>();
        for (Clash c : CLASHES) if (c.body().equals(body)) out.add(c);
        return out;
    }

    /** Forgets this body's clashes (a scene starts clean). */
    public static void forget(String body) {
        CLASHES.removeIf(c -> c.body().equals(body));
    }
}
