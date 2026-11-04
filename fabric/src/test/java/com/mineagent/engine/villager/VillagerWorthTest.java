package com.mineagent.engine.villager;

import com.mineagent.engine.villager.VillagerWorth.Offer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VillagerWorthTest {
    static Offer book(String ench, int level, int max) {
        return new Offer("minecraft:enchanted_book", "minecraft:" + ench, level, max, 20);
    }

    static final Offer PAPER = new Offer("minecraft:emerald", null, 0, 0, 0);
    static final Offer BOOKSHELF = new Offer("minecraft:bookshelf", null, 0, 0, 9);

    @Test void mendingAboveAllThenTopBooks() {
        assertEquals(100, VillagerWorth.worth(book("mending", 1, 1)));
        assertEquals(60, VillagerWorth.worth(book("protection", 4, 4)));
        assertEquals(25, VillagerWorth.worth(book("efficiency", 4, 5)));
        assertEquals(5, VillagerWorth.worth(book("efficiency", 2, 5)));
        assertEquals(5, VillagerWorth.worth(book("binding_curse", 1, 1)), "a curse is worth nothing much");
    }

    @Test void aMendingLibrarianIsKeptAnOrdinaryOneIsNot() {
        assertTrue(VillagerWorth.keeper(List.of(PAPER, book("mending", 1, 1), BOOKSHELF)));
        assertTrue(VillagerWorth.sells(List.of(PAPER, book("mending", 1, 1)), "minecraft:mending"));
        assertFalse(VillagerWorth.keeper(List.of(PAPER, book("bane_of_arthropods", 3, 5), BOOKSHELF)));
        assertTrue(VillagerWorth.keeper(List.of(PAPER, book("unbreaking", 3, 3))), "Unbreaking III is worth a cell");
        assertFalse(VillagerWorth.sells(List.of(book("unbreaking", 2, 3)), "minecraft:unbreaking"), "not at its top level");
        assertFalse(VillagerWorth.keeper(List.of(new Offer("minecraft:bread", null, 0, 0, 1))));
        assertTrue(VillagerWorth.keeper(List.of(new Offer("minecraft:ender_pearl", null, 0, 0, 5), new Offer("minecraft:experience_bottle", null, 0, 0, 3))));
    }

    @Test void levelsAsTheGameCountsThem() {
        assertEquals(1, VillagerWorth.level(0));
        assertEquals(2, VillagerWorth.level(10));
        assertEquals(3, VillagerWorth.level(70));
        assertEquals(4, VillagerWorth.level(150));
        assertEquals(5, VillagerWorth.level(250));
        assertEquals(240, VillagerWorth.toMaster(10));
        assertEquals(0, VillagerWorth.toMaster(400));
    }

    static VillagerWorth.Deal deal(int i, String cost, int n, String result, int xp, int uses) {
        return new VillagerWorth.Deal(i, "minecraft:" + cost, n, "minecraft:" + result, 1, xp, uses);
    }

    @Test void aKeeperIsTrainedWithItsCheapGoodsFirst() {
        var deals = List.of(
                deal(0, "paper", 24, "emerald", 2, 16),            // a novice librarian buys paper
                deal(1, "emerald", 9, "bookshelf", 1, 12),         // and sells bookshelves
                deal(2, "diamond", 1, "emerald", 30, 12));         // never paid in diamonds, whatever it is worth
        var has = java.util.Map.of("minecraft:emerald", 40);
        var d = VillagerWorth.toTrain(deals, has, item -> item.equals("minecraft:paper"));
        assertEquals(0, d.index(), "paper it can make, sold for emeralds");
        assertEquals(12, VillagerWorth.trainTimes(d, has, 250), "a round's worth, all of it made");
        assertEquals(1, VillagerWorth.trainTimes(d, has, 2), "no more than take it to master");
        var some = java.util.Map.of("minecraft:paper", 50);
        assertEquals(2, VillagerWorth.trainTimes(d, some, 250), "with the paper it carries: what that pays for");
        assertNull(VillagerWorth.toTrain(deals, java.util.Map.of("minecraft:emerald", 20), item -> false),
                "nothing it can make, and spending 9 would leave less than the reserve");
        var spend = VillagerWorth.toTrain(deals, java.util.Map.of("minecraft:emerald", 40), item -> false);
        assertEquals(1, spend.index(), "emeralds above the reserve: spent on a trade that trains it");
        assertEquals(2, VillagerWorth.trainTimes(spend, java.util.Map.of("minecraft:emerald", 40), 250), "no deeper than the reserve");
        assertNull(VillagerWorth.toTrain(List.of(deal(0, "paper", 24, "emerald", 2, 0)), some, item -> true), "no uses left: it restocks first");
        assertEquals(0, VillagerWorth.trainTimes(d, has, 0), "a master needs no more");
    }
}

