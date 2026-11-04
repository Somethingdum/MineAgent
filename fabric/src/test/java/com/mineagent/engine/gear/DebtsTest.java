package com.mineagent.engine.gear;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DebtsTest {
    @Test void aTakingIsOwedAndComesOffAsItIsGivenBack() {
        var d = new Debts();
        d.took("u1", "Sam", Map.of("minecraft:diamond", 12, "minecraft:iron_ingot", 3), 100, false);
        d.took("u1", "Sam", Map.of("minecraft:diamond", 2), 200, false);
        assertEquals(14, d.of("u1").orElseThrow().owed().get("minecraft:diamond").intValue(), "a second taking adds to the first");
        assertEquals(100, d.of("u1").orElseThrow().since());
        var off = d.gave("u1", Map.of("minecraft:diamond", 10, "minecraft:dirt", 64));
        assertEquals(Map.of("minecraft:diamond", 10), off, "only what they owe comes off: dirt pays nothing");
        assertEquals(4, d.of("u1").orElseThrow().owed().get("minecraft:diamond").intValue());
        d.gave("u1", Map.of("minecraft:diamond", 9, "minecraft:iron_ingot", 3));
        assertTrue(d.of("u1").isEmpty(), "all of it back: square");
    }

    @Test void forgivenAndFoundByName() {
        var d = new Debts();
        d.took("u1", "Sam", Map.of("minecraft:bread", 5), 0, true);
        assertTrue(d.of("u1").orElseThrow().revenge());
        assertEquals("u1", d.byName("sam"));
        d.forgive("u1");
        assertTrue(d.isEmpty());
        assertTrue(d.gave("u1", Map.of("minecraft:bread", 5)).isEmpty(), "nothing owed: nothing taken off");
    }

    @Test void itRemembersAtMostThirtyTwoPlayers() {
        var d = new Debts();
        for (int i = 0; i < 40; i++) d.took("u" + i, "P" + i, Map.of("minecraft:stick", 1), i, false);
        assertEquals(Debts.MOST_PLAYERS, d.all().size());
        assertTrue(d.of("u0").isEmpty(), "the oldest forgotten first");
        assertTrue(d.of("u39").isPresent());
    }

    @Test void savedAndLoaded() {
        var d = new Debts();
        d.took("u1", "Sam", Map.of("minecraft:diamond", 3), 5, false);
        var e = new Debts();
        e.load(d.all());
        assertEquals(3, e.of("u1").orElseThrow().owed().get("minecraft:diamond").intValue());
    }

    @Test void saidAsAPersonWouldSayIt() {
        var m = new LinkedHashMap<String, Integer>();
        m.put("minecraft:iron_ingot", 3);
        m.put("minecraft:diamond", 12);
        m.put("minecraft:bow", 1);
        assertEquals("12 diamonds, 3 iron ingots and 1 bow", Debts.describe(m));
        assertEquals("1 diamond", Debts.describe(Map.of("minecraft:diamond", 1)));
    }
}
