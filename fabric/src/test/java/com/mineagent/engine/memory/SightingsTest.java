package com.mineagent.engine.memory;

import com.mineagent.engine.memory.Sightings.Kind;
import com.mineagent.engine.memory.store.MemoryStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA5.7: things it has seen, kept by region, found nearest first, forgotten by worth. */
final class SightingsTest {
    private static final String OW = "minecraft:overworld";

    @Test void namesAreKeptPlainAndWordsFindThem() {
        assertEquals("iron", Sightings.base("minecraft:deepslate_iron_ore"));
        assertEquals("quartz", Sightings.base("minecraft:nether_quartz_ore"));
        assertEquals("cow", Sightings.base("minecraft:cow"));
        assertEquals("diamond", Sightings.fromWords("diamonds"));
        assertEquals("iron", Sightings.fromWords("the iron ore"));
        assertEquals("cow", Sightings.fromWords("cows"));
        assertEquals("lapis", Sightings.fromWords("lapis"));
        assertEquals("ancient_debris", Sightings.fromWords("ancient debris"));
    }

    @Test void theNearestIsFoundAndCloseSightingsMerge() {
        Sightings s = new Sightings(null, 1000);
        s.see(Kind.ORE, "minecraft:iron_ore", OW, 100, 20, 100, 1, 10);
        s.see(Kind.ORE, "minecraft:deepslate_iron_ore", OW, 101, 21, 101, 3, 20); // same 4-block bucket
        s.see(Kind.ORE, "minecraft:iron_ore", OW, 2000, 20, 2000, 1, 30); // another region
        s.see(Kind.ORE, "minecraft:diamond_ore", OW, 110, -50, 100, 2, 40);
        assertEquals(3, s.count());
        var found = s.nearest("iron", OW, 0, 64, 0, 5, 10_000);
        assertEquals(2, found.size());
        assertEquals(3, found.get(0).sighting().count());
        assertEquals(100, found.get(0).sighting().x());
        assertEquals(1, s.nearest("ORE", OW, 105, 0, 100, 1, 10_000).size());
        assertTrue(s.nearest("iron", "minecraft:the_nether", 0, 64, 0, 5, 10_000).isEmpty());
        assertTrue(s.known("diamond"));
        assertFalse(s.known("emerald"));
    }

    @Test void whatIsMinedOutIsStruckOff() {
        Sightings s = new Sightings(null, 1000);
        s.see(Kind.ORE, "minecraft:coal_ore", OW, 5, 60, 5, 1, 1);
        s.gone(Kind.ORE, "minecraft:coal_ore", OW, 5, 60, 5);
        assertEquals(0, s.count());
        assertTrue(s.nearest("coal", OW, 5, 60, 5, 5, 100).isEmpty());
        assertFalse(s.known("coal"));
    }

    @Test void whenFullTheLeastWorthGoesFirst() {
        Sightings s = new Sightings(null, 100);
        s.see(Kind.ORE, "minecraft:diamond_ore", OW, 0, 10, 0, 1, 0);
        for (int i = 0; i < 150; i++) s.see(Kind.MOB, "minecraft:zombie", OW, i * 16, 64, 0, 1, 1000 + i);
        assertTrue(s.count() <= 100);
        assertEquals(1, s.nearest("diamond", OW, 0, 10, 0, 1, 100).size(), "a diamond outlasts a crowd of zombies");
    }

    @Test void itKeepsThroughARestartAndAfterACrash() throws Exception {
        Path dir = Files.createTempDirectory("sightings");
        MemoryStore st = new MemoryStore(dir);
        Sightings a = new Sightings(st, 1000);
        a.see(Kind.ORE, "minecraft:gold_ore", OW, -700, 12, 300, 4, 5);
        a.see(Kind.BLOCK, "minecraft:spawner", OW, 40, 30, 40, 1, 6);
        st.flushBlocking();
        a.see(Kind.ORE, "minecraft:emerald_ore", OW, 1500, 40, -1500, 1, 7); // journal only: then a crash
        MemoryStore st2 = new MemoryStore(dir);
        Sightings b = new Sightings(st2, 1000);
        st2.replayJournals();
        assertEquals(3, b.count());
        assertEquals(4, b.nearest("gold", OW, 0, 0, 0, 1, 5000).get(0).sighting().count());
        assertEquals(1, b.nearest("emerald", OW, 0, 0, 0, 1, 5000).size());
        assertEquals(1, b.nearest("spawner", OW, 0, 0, 0, 1, 5000).size());
        var report = st2.report();
        assertEquals(Long.valueOf(3L), report.counts().get("sightings"));
        st2.close();
    }
    /** The quickest of {@code n} runs, in milliseconds: one slow moment on a shared CI machine is not the code. */
    private static double bestOf(int n, java.util.concurrent.Callable<?> work) throws Exception {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            long t = System.nanoTime();
            work.call();
            best = Math.min(best, (System.nanoTime() - t) / 1e6);
        }
        return best;
    }

    /**
     * MA5.7's sizes, measured: 50,000 things seen over 10,000 by 10,000 blocks (400 regions). A changed region is saved
     * in under 20 ms, the memory opened again in under 200 ms, and a lookup takes under 1 ms.
     */
    @Test void atFullSizeItIsQuick() throws Exception {
        Path dir = Files.createTempDirectory("sightings-full");
        MemoryStore st = new MemoryStore(dir);
        Sightings a = new Sightings(st, Sightings.DEFAULT_MAX);
        java.util.Random r = new java.util.Random(70);
        String[] ores = {"minecraft:iron_ore", "minecraft:coal_ore", "minecraft:copper_ore", "minecraft:gold_ore",
                "minecraft:redstone_ore", "minecraft:lapis_ore", "minecraft:diamond_ore"};
        long tick = 1;
        while (a.count() < Sightings.DEFAULT_MAX) {
            int x = r.nextInt(10_000) - 5_000, z = r.nextInt(10_000) - 5_000, y = r.nextInt(120) - 60;
            Kind k = r.nextInt(4) == 0 ? Kind.ANIMAL : Kind.ORE;
            a.see(k, k == Kind.ORE ? ores[r.nextInt(ores.length)] : "minecraft:cow", OW, x, y, z, 1, tick++);
        }
        st.flushBlocking();
        assertEquals(Sightings.DEFAULT_MAX, a.count());

        long[] t = {tick};
        double save = bestOf(5, () -> {
            a.see(Kind.ORE, "minecraft:emerald_ore", OW, 100 + (int) (t[0] % 50), 20, 100, 1, t[0]++);
            st.flushBlocking();
            return null;
        });
        assertTrue(save < 20.0, "a changed region saved in " + save + " ms");
        long kept = a.count();
        assertTrue(kept >= Sightings.DEFAULT_MAX * 99L / 100, "full, it trims to 99%, not more: " + kept);

        double open = bestOf(3, () -> {
            MemoryStore again = new MemoryStore(dir);
            Sightings b = new Sightings(again, Sightings.DEFAULT_MAX);
            again.replayJournals();
            if (b.count() != kept) throw new AssertionError("reopened with " + b.count() + " of " + kept);
            again.close();
            return null;
        });
        assertTrue(open < 200.0, "opened again in " + open + " ms");

        // Lookups where it lives (within 600 blocks: its regions stay loaded), and anywhere in the 10,000 blocks
        // (most regions read back from disk first: the worst case).
        for (int i = 0; i < 50; i++) a.nearest("iron", OW, r.nextInt(1_200) - 600, 0, r.nextInt(1_200) - 600, 3, 256);
        int lookups = 200;
        long started = System.nanoTime();
        for (int i = 0; i < lookups; i++) a.nearest("diamond", OW, r.nextInt(1_200) - 600, 0, r.nextInt(1_200) - 600, 3, 256);
        double each = (System.nanoTime() - started) / 1e6 / lookups;
        started = System.nanoTime();
        for (int i = 0; i < lookups; i++) a.nearest("diamond", OW, r.nextInt(10_000) - 5_000, 0, r.nextInt(10_000) - 5_000, 3, 256);
        double anywhere = (System.nanoTime() - started) / 1e6 / lookups;
        assertTrue(each < 1.0, "a lookup where it lives in " + each + " ms");
        assertTrue(anywhere < 5.0, "a lookup anywhere in " + anywhere + " ms");
        System.out.printf("[MA5.7 sizes] %d things seen: a changed region saved in %.1f ms, opened again in %.0f ms, "
                + "a lookup in %.3f ms where it lives and %.3f ms anywhere%n", kept, save, open, each, anywhere);
        st.close();
    }
}
