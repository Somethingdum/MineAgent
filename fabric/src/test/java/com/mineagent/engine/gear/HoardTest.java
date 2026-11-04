package com.mineagent.engine.gear;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HoardTest {
    @Test void foodFirstThenTheRestInOrderInBatches() {
        var t = Hoard.next(Map.of(), x -> true);
        assertEquals("minecraft:bread", t.item());
        assertEquals(64, t.count());
        var fed = Hoard.next(Map.of("minecraft:bread", 64, "minecraft:cooked_beef", 64), x -> true);
        assertEquals("minecraft:torch", fed.item());
        assertEquals(64, fed.count(), "a batch at a time");
    }

    @Test void whatCantBeMadeHereIsSkipped() {
        var t = Hoard.next(Map.of("minecraft:bread", 64), x -> !x.item().equals("minecraft:cooked_beef"));
        assertEquals("minecraft:torch", t.item(), "no cows here: torches next");
        assertNull(Hoard.next(Map.of(), x -> false));
    }

    @Test void theMetricTonComesAfterTheBasics() {
        java.util.Map<String, Integer> all = new java.util.HashMap<>();
        for (var t : Hoard.TARGETS.subList(0, 15)) all.merge(t.item(), t.count(), Math::max); // the basics, slimeballs last
        var t = Hoard.next(all, x -> true);
        assertEquals("minecraft:golden_carrot", t.item());
        assertEquals(64, t.count(), "toward 512, a batch at a time");
    }
}
