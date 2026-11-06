package com.mineagent.engine.rules;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SMH plan 71, §6.2 rule 4: one rule predicate for a companion's breaks and placements, asked at three levels. It is
 * registered once per companion, when its AltoClef host is made, with Automatone's per-body AltoClef settings, so no
 * path plans the break and no task's behaviour stack can pop it; AltoClef's tasks ask the same settings; and the
 * body's game mode asks it again at the bottom, refuses a break it forbids, and records a {@link Mismatch} (I3): a
 * refusal the pathfinder did not know about is how a companion retries the same break forever (world 1 of run
 * 117f23f lost a day so). Built from the companion's own rules (SmhRules: blocks people placed, with "never break
 * built blocks" on; farms whose player is AFK and the areas an owner protected). Written for this mod.
 */
public final class BlockRules {
    /** A refusal at the bottom: who asked for what, where. */
    public record Mismatch(String body, String action, BlockPos pos, String asker) {
        @Override
        public String toString() {
            return "rule mismatch: " + body + " asked to " + action + " " + pos.toShortString() + " (" + asker + ")";
        }
    }

    private static final ConcurrentLinkedQueue<Mismatch> MISMATCHES = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger TOTAL = new AtomicInteger();

    private BlockRules() {}

    /** Whether a companion may break the block at {@code pos}. Safe on any thread (path threads ask it). */
    public static boolean mayBreak(ServerLevel level, BlockPos pos) {
        return !SmhRules.placedByAPerson(level, pos) && !SmhRules.afkFarm(level, pos);
    }

    /** Whether a companion may place a block at {@code pos}. Safe on any thread. */
    public static boolean mayPlace(ServerLevel level, BlockPos pos) {
        return !SmhRules.afkFarm(level, pos);
    }

    /** A break or placement the rules forbid, asked for anyway; logged once in a while, kept for the scenes. */
    public static void mismatch(ServerPlayer body, String action, BlockPos pos, String asker) {
        Mismatch m = new Mismatch(body.getGameProfile().getName(), action, pos.immutable(), asker);
        MISMATCHES.add(m);
        while (MISMATCHES.size() > 200) MISMATCHES.poll();
        int n = TOTAL.incrementAndGet();
        if (n <= 20 || n % 100 == 0) System.out.println("[MineAgent] " + m + (n > 20 ? " (" + n + " so far)" : ""));
    }

    /** Every mismatch kept since the last take, taken out (the scenes fail on any, I3). */
    public static List<Mismatch> takeAll() {
        List<Mismatch> out = new ArrayList<>();
        for (Mismatch m; (m = MISMATCHES.poll()) != null; ) out.add(m);
        return out;
    }

    /** How many there have been in this run. */
    public static int total() {
        return TOTAL.get();
    }
}
