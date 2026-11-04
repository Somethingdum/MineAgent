package com.mineagent.engine.villager;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VillagerCellTest {
    static boolean blocked(VillagerCell.Layout l, VillagerCell.Pos p, boolean doorShut) {
        return l.walls().contains(p) || p.equals(l.jobSite()) || doorShut && p.equals(l.door());
    }

    @Test void shutInItCanGoNowhere() {
        for (int[] d : VillagerCell.DIRECTIONS) {
            var l = VillagerCell.plan(0, 64, 0, d[0], d[1]);
            var c = l.cell();
            // Every way out of the cell, at feet and head: a wall, the job site, or the door (feet) and the gap over it,
            // a block high, too low for a villager.
            for (int[] n : VillagerCell.DIRECTIONS) {
                var feet = new VillagerCell.Pos(c.x() + n[0], c.y(), c.z() + n[1]);
                var head = new VillagerCell.Pos(c.x() + n[0], c.y() + 1, c.z() + n[1]);
                assertTrue(blocked(l, feet, true), "its feet can't go " + n[0] + "," + n[1]);
                if (!feet.equals(l.door()) && !feet.equals(l.jobSite())) assertTrue(blocked(l, head, true), "nor its head");
            }
            assertTrue(l.walls().contains(new VillagerCell.Pos(c.x(), c.y() + 2, c.z())), "a roof");
            assertTrue(l.walls().contains(new VillagerCell.Pos(l.door().x(), l.door().y() + 2, l.door().z())),
                    "nothing to climb out on over the door block");
            assertTrue(l.walls().contains(new VillagerCell.Pos(l.jobSite().x(), l.jobSite().y() + 2, l.jobSite().z())),
                    "nor over its job site");
        }
    }

    @Test void openItWalksIn() {
        var l = VillagerCell.plan(10, 64, 10, 1, 0);
        assertEquals(new VillagerCell.Pos(11, 64, 10), l.cell());
        assertEquals(new VillagerCell.Pos(12, 64, 10), l.way());
        assertFalse(blocked(l, l.way(), false), "the way in is open till it is in");
        assertFalse(l.walls().contains(new VillagerCell.Pos(12, 65, 10)), "and two high");
        assertEquals(new VillagerCell.Pos(13, 64, 10), l.tradeFrom(), "traded with from outside the door");
    }

    @Test void itCanWorkFromNowhereElse() {
        var l = VillagerCell.plan(0, 64, 0, 0, 1);
        var j = l.jobSite();
        Set<VillagerCell.Pos> round = new HashSet<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0) round.add(new VillagerCell.Pos(j.x() + x, j.y(), j.z() + z));
        round.remove(l.cell());
        for (var p : round) assertTrue(l.walls().contains(p), "no other spot by its job site: " + p);
        assertEquals(18, l.walls().size(), "seven cells walled two high, three roofs and the block they hang from");
    }

    @Test void eachBlockGoesAgainstOneAlreadyThere() {
        // The game puts a block down only against another (the ground, the job site, a wall before it): in the order
        // given, every one can go in.
        for (int[] d : VillagerCell.DIRECTIONS) {
            var l = VillagerCell.plan(0, 64, 0, d[0], d[1]);
            Set<VillagerCell.Pos> there = new HashSet<>();
            there.add(l.jobSite());
            for (var p : l.walls()) {
                boolean against = p.y() == 64; // the ground under it
                for (int[] f : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                    if (there.contains(new VillagerCell.Pos(p.x() + f[0], p.y() + f[1], p.z() + f[2]))) against = true;
                }
                assertTrue(against, "nothing to go against at " + p + " going " + d[0] + "," + d[1]);
                there.add(p);
            }
            // The door too, once the walls are up.
            assertTrue(l.door().y() == 64);
        }
    }
}
