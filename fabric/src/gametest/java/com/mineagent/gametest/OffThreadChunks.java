package com.mineagent.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * SMH plan 71, invariant I1: no chunk is read, loaded or generated off the server thread by anything of ours or the
 * ported code. The guard ({@code mixin/OffThreadChunkGuardMixin}) brings here each call of the server's chunk map
 * ({@code ServerChunkCache.getChunk} or {@code getChunkFuture}, what {@code Level.getBlockState} and every other world
 * read end in) made off the server thread; one whose stack passes through our code ({@code com.mineagent.}) is
 * recorded, and its place is printed the first time it is seen. A scene that would succeed while the record holds
 * anything fails with it instead ({@code mixin/SceneResultMixin}), so the suite fails if the record is not empty.
 * A call with none of our code on its stack (the game's own, another mod's) is not ours to answer for, and is left.
 */
public final class OffThreadChunks {
    /** One call: the thread it came from, the chunk, and the first place in our code it passed through. */
    public record Call(String thread, int chunkX, int chunkZ, String status, boolean load, String ours) {
        @Override
        public String toString() {
            return "chunk " + chunkX + ", " + chunkZ + " (" + status + (load ? ", loading" : "") + ") on \"" + thread + "\" from " + ours;
        }
    }

    private static final ConcurrentLinkedQueue<Call> RECORD = new ConcurrentLinkedQueue<>();
    private static final Set<String> PRINTED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger TOTAL = new AtomicInteger();

    private OffThreadChunks() {}

    /** From the guard: a chunk asked for off the server thread. */
    public static void offThread(int chunkX, int chunkZ, Object status, boolean load) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        String ours = null;
        StringBuilder where = new StringBuilder();
        int shown = 0;
        for (StackTraceElement e : stack) {
            if (e.getClassName().equals(OffThreadChunks.class.getName())) continue;
            if (ours == null && e.getClassName().startsWith("com.mineagent.")) ours = e.toString();
            if (shown++ < 16) where.append("\n    at ").append(e);
        }
        if (ours == null) return;
        Call call = new Call(Thread.currentThread().getName(), chunkX, chunkZ, String.valueOf(status), load, ours);
        RECORD.add(call);
        TOTAL.incrementAndGet();
        if (PRINTED.add(ours)) System.out.println("[MineAgent] I1: a chunk asked for off the server thread, " + call + where);
    }

    /** Every call recorded since the last take, taken out of the record. */
    public static List<Call> takeAll() {
        return drain(c -> true);
    }

    /** The recorded calls this test matches, taken out of the record (the guard's own scene takes its own). */
    public static List<Call> drain(Predicate<Call> which) {
        List<Call> out = new ArrayList<>();
        for (var it = RECORD.iterator(); it.hasNext(); ) {
            Call c = it.next();
            if (which.test(c)) {
                out.add(c);
                it.remove();
            }
        }
        return out;
    }

    /** How many calls were recorded in this run, taken or not. */
    public static int total() {
        return TOTAL.get();
    }
}
