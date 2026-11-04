package com.mineagent.engine.planning.production;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** What it keeps and what is promised (SMH plan 70, MA7.4). */
final class ReservesTest {
    @Test void theFloorsAreNotSpentOnAnOrder() {
        var carried = Map.of("minecraft:cooked_beef", 10, "minecraft:bread", 3, "minecraft:torch", 20, "minecraft:coal", 5);
        var free = Reserves.afterFloors(carried, Set.of("minecraft:cooked_beef", "minecraft:bread"), 8, 16);
        assertEquals(5, free.getOrDefault("minecraft:cooked_beef", 0).intValue() + free.getOrDefault("minecraft:bread", 0).intValue(),
                "13 food less the 8 it keeps");
        assertEquals(4, free.get("minecraft:torch").intValue(), "20 torches less 16");
        assertEquals(5, free.get("minecraft:coal").intValue(), "coal is not kept");
        var none = Reserves.afterFloors(Map.of("minecraft:cooked_beef", 6), Set.of("minecraft:cooked_beef"), 8, 16);
        assertFalse(none.containsKey("minecraft:cooked_beef"), "all of it is its trip food");
    }

    @Test void twoJobsDontCountOnTheSameIron() {
        var ledger = new Reserves.Ledger();
        var chests = Map.of("minecraft:iron_ingot", 5, "minecraft:coal", 9);
        ledger.reserve("shield", Map.of("minecraft:iron_ingot", 3));
        assertEquals(2, ledger.free(chests, "bucket").get("minecraft:iron_ingot").intValue(), "3 of the 5 are the shield's");
        assertEquals(5, ledger.free(chests, "shield").get("minecraft:iron_ingot").intValue(), "its own reservation is its own");
        ledger.release("shield");
        assertEquals(5, ledger.free(chests, "bucket").get("minecraft:iron_ingot").intValue());
    }

    @Test
    void someFuelIsAlwaysKeptBack() {
        var left = Reserves.afterFuelFloor(Map.of("minecraft:coal", 5, "minecraft:charcoal", 10, "minecraft:oak_log", 4), Reserves.FUEL_FLOOR);
        assertEquals(0, left.getOrDefault("minecraft:coal", 0).intValue(), "coal kept first");
        assertEquals(7, left.get("minecraft:charcoal").intValue(), "then charcoal, eight in all");
        assertEquals(4, left.get("minecraft:oak_log").intValue(), "nothing else held back");
        assertTrue(Reserves.afterFuelFloor(Map.of("minecraft:coal", 3), 8).isEmpty(), "less than the floor: none to spend");
    }
}
