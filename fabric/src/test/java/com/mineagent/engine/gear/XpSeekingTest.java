package com.mineagent.engine.gear;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XpSeekingTest {
    @Test void itWantsThirtyLevelsOrWhatAnAnvilRunTakes() {
        assertEquals(30, XpSeeking.target(0), "the best enchanting table roll");
        assertEquals(42, XpSeeking.target(42), "a run of anvil steps it couldn't pay for");
        assertEquals(50, XpSeeking.target(90), "never more than 50");
    }

    @Test void doingWellIsIronAllOverAWeaponAndAShield() {
        assertTrue(XpSeeking.geared(3, 4, 3, 3, 3, true));
        assertFalse(XpSeeking.geared(3, 3, 3, 1, 4, true), "leather boots: not yet");
        assertFalse(XpSeeking.geared(3, 3, 3, 3, 2, true), "a stone sword: not yet");
        assertFalse(XpSeeking.geared(4, 4, 4, 4, 4, false), "no shield: not yet");
    }

    @Test void doingWellForMaterialsIsFoodIronCoalAndTorches() {
        assertEquals(4, XpSeeking.short_(Map.of()).size());
        var enough = Map.of("minecraft:cooked_beef", 20, "minecraft:bread", 12, "minecraft:iron_ingot", 40,
                "minecraft:charcoal", 32, "minecraft:torch", 64);
        assertTrue(XpSeeking.short_(enough).isEmpty(), "charcoal counts as coal; food of any kind adds up");
        assertEquals(List.of("8 more iron"), XpSeeking.short_(Map.of("minecraft:cooked_beef", 64, "minecraft:iron_ingot", 24,
                "minecraft:coal", 40, "minecraft:torch", 100)));
    }

    @Test void fitToStartAndToGoOn() {
        assertTrue(XpSeeking.fit(16, 20, 18, 10));
        assertFalse(XpSeeking.fit(12, 20, 18, 10), "hurt");
        assertFalse(XpSeeking.fit(20, 20, 10, 10), "hungry");
        assertFalse(XpSeeking.fit(20, 20, 20, 4), "too little food on it");
        assertTrue(XpSeeking.goOn(10, 20, 2));
        assertFalse(XpSeeking.goOn(9, 20, 5), "under half: it stops");
    }

    @Test void nightIsDuskToDawn() {
        assertTrue(XpSeeking.night(18_000));
        assertTrue(XpSeeking.night(13_000));
        assertFalse(XpSeeking.night(23_000));
        assertFalse(XpSeeking.night(6_000));
        assertTrue(XpSeeking.night(24_000L * 5 + 15_000), "any day's night");
    }

    @Test void whatItGoesAfter() {
        assertEquals(XpSeeking.Kind.HOSTILE, XpSeeking.kind("minecraft:zombie", true));
        assertEquals(XpSeeking.Kind.HOSTILE, XpSeeking.kind("minecraft:enderman", true), "of any kind");
        assertEquals(XpSeeking.Kind.NEVER, XpSeeking.kind("minecraft:creeper", true), "met as it comes, never walked up to");
        assertEquals(XpSeeking.Kind.NEVER, XpSeeking.kind("minecraft:zombified_piglin", true), "its kin all turn on it");
        assertEquals(XpSeeking.Kind.NEVER, XpSeeking.kind("minecraft:warden", true));
        assertEquals(XpSeeking.Kind.ANIMAL, XpSeeking.kind("minecraft:cow", false));
        assertEquals(XpSeeking.Kind.NEVER, XpSeeking.kind("minecraft:villager", false));
        assertEquals(XpSeeking.Kind.NEVER, XpSeeking.kind("minecraft:wolf", false));
    }

    @Test void aMonsterBeforeAnyAnimalAndTheHerdKept() {
        var cow = new XpSeeking.Prey("minecraft:cow", false, 4, true, false, true, false, 5);
        var zombie = new XpSeeking.Prey("minecraft:zombie", true, 20, true, false, true, false, 0);
        assertEquals(1, XpSeeking.pick(List.of(cow, zombie)), "the monster, though the cow is nearer");
        assertEquals(0, XpSeeking.pick(List.of(cow)), "a cow of five");
        assertEquals(-1, XpSeeking.pick(List.of(new XpSeeking.Prey("minecraft:cow", false, 4, true, false, true, false, 2))),
                "never one of the last two");
        assertEquals(-1, XpSeeking.pick(List.of(new XpSeeking.Prey("minecraft:cow", false, 4, true, false, false, false, 9))), "never a calf");
        assertEquals(-1, XpSeeking.pick(List.of(new XpSeeking.Prey("minecraft:cow", false, 4, true, false, true, true, 9))),
                "never one that is kept (named, on a lead, penned, by home)");
        assertEquals(-1, XpSeeking.pick(List.of(new XpSeeking.Prey("minecraft:zombie", true, 6, true, true, true, false, 0))), "not in water");
        assertEquals(-1, XpSeeking.pick(List.of(new XpSeeking.Prey("minecraft:zombie", true, 20, false, false, true, false, 0))),
                "not one out of sight beyond 12");
        var blaze = new XpSeeking.Prey("minecraft:blaze", true, 15, true, false, true, false, 0);
        var skeleton = new XpSeeking.Prey("minecraft:skeleton", true, 10, true, false, true, false, 0);
        assertEquals(0, XpSeeking.pick(List.of(blaze, skeleton)), "ten levels' worth before five");
    }
}
