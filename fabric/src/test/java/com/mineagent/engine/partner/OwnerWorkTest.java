package com.mineagent.engine.partner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA10.4: what its owner is doing, from the blocks they break and place. */
final class OwnerWorkTest {
    @Test void stoneBrokenIsMining() {
        var w = new OwnerWork();
        for (int i = 0; i < 5; i++) w.broke(i % 2 == 0 ? "minecraft:stone" : "minecraft:iron_ore", i, 12, 0, 100 + i * 20);
        assertEquals(OwnerWork.Doing.MINING, w.doing(200));
        assertEquals(5, w.trail(200).size());
    }

    @Test void blocksPlacedAreBuildingAndTheMostPlacedIsNamed() {
        var w = new OwnerWork();
        for (int i = 0; i < 6; i++) w.placed("minecraft:oak_planks", i, 65, 0, 10 + i * 10);
        w.placed("minecraft:glass", 9, 65, 0, 90);
        assertEquals(OwnerWork.Doing.BUILDING, w.doing(100));
        assertEquals("minecraft:oak_planks", w.placing(100));
    }

    @Test void aFewBlocksOrOldOnesAreNothing() {
        var w = new OwnerWork();
        w.broke("minecraft:stone", 0, 0, 0, 0);
        w.broke("minecraft:stone", 1, 0, 0, 10);
        assertEquals(OwnerWork.Doing.NOTHING, w.doing(20), "two breaks are not a job yet");
        for (int i = 0; i < 6; i++) w.broke("minecraft:stone", i, 0, 0, 100);
        assertEquals(OwnerWork.Doing.MINING, w.doing(200));
        assertEquals(OwnerWork.Doing.NOTHING, w.doing(100 + OwnerWork.WINDOW + 1), "half a minute on, forgotten");
        w.broke("minecraft:oak_log", 0, 70, 0, 2_000);
        w.broke("minecraft:oak_log", 0, 71, 0, 2_000);
        w.broke("minecraft:oak_log", 0, 72, 0, 2_000);
        w.broke("minecraft:oak_log", 0, 73, 0, 2_000);
        assertEquals(OwnerWork.Doing.NOTHING, w.doing(2_010), "felling a tree is not mining");
    }
}
