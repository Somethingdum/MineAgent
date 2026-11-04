package com.mineagent.api.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, Switches: each block 1 behaviour has a setting, on by default, kept across older config files. */
final class BehaviourSwitchesTest {
    @Test void aFileFromBeforeTheSwitchesHasThemAllOn() throws Exception {
        Path dir = Files.createTempDirectory("mineagent-config");
        Files.writeString(dir.resolve("mineagent.json"), "{\"companion\":{\"name\":\"Pal\"}}");
        MineAgentConfig config = MineAgentConfig.load(dir);
        assertEquals("Pal", config.companion().name());
        assertEquals(MineAgentConfig.BehaviourConfig.DEFAULTS, config.behaviour());
        assertTrue(config.behaviour().neverBreakBuilt());
        assertEquals(3, config.behaviour().afkMinutes());
    }

    @Test void aSwitchTurnedOffStaysOffAndTimesAreKeptInRange() throws Exception {
        Path dir = Files.createTempDirectory("mineagent-config");
        Files.writeString(dir.resolve("mineagent.json"), "{\"behaviour\":{\"sprint\":false,\"afkMinutes\":999}}");
        MineAgentConfig config = MineAgentConfig.load(dir);
        assertFalse(config.behaviour().sprint());
        assertTrue(config.behaviour().closeDoors());
        assertEquals(60, config.behaviour().afkMinutes());
    }

    @Test void theOlderConstructorGivesTheDefaults() {
        var c = MineAgentConfig.DEFAULTS;
        var same = new MineAgentConfig(c.llm(), c.companion(), c.survival(), c.pathfinding());
        assertEquals(MineAgentConfig.BehaviourConfig.DEFAULTS, same.behaviour());
    }

    @Test void memorySizesHaveDefaultsAndStayInRange() throws Exception {
        Path dir = Files.createTempDirectory("mineagent-config");
        Files.writeString(dir.resolve("mineagent.json"), "{\"memory\":{\"thingsSeen\":5,\"journalDays\":30}}");
        MineAgentConfig config = MineAgentConfig.load(dir);
        assertEquals(1_000, config.memory().thingsSeen());
        assertEquals(30, config.memory().journalDays());
        assertEquals(4_096, config.memory().chests());
        assertEquals(50_000, MineAgentConfig.DEFAULTS.memory().thingsSeen());
    }

    @Test void afkChangesNothingOfItsJobsOrDistanceUnlessAsked() throws Exception {
        var d = MineAgentConfig.BehaviourConfig.DEFAULTS;
        assertFalse(d.afkGuardMe(), "guarding an AFK owner is optional, off");
        assertEquals(0, d.afkStayWithin(), "no distance to keep within");
        assertTrue(d.afkFarmLeftAlone(), "a farm they're AFK at is still left alone");
        assertTrue(d.afkKeepBusy(), "AFK just means away: it keeps busy");
        assertTrue(d.huntForOrders(), "food gathering was asked for: it hunts for an order");
        assertTrue(d.oreSightOnly(), "fair play: only ore it has seen");
        assertTrue(d.readyCheck(), "ready before a trip");
        assertEquals(8, d.readyFood());
        assertEquals(16, d.readyTorches());
        assertTrue(d.sparePickaxe());
        assertTrue(d.escapeLavaFire(), "a check that only prevents harm: on");
        assertTrue(d.sleepWhenAway(), "nights when you're away: in bed, or sheltered");
        assertTrue(d.liveOnOwn(), "living on its own, no model needed");
        assertEquals(64, d.primeMeat(), "prime a world: 64 meat each");
        assertEquals(32, d.primeTorches());
        assertTrue(d.primeFullIron());
        assertTrue(d.manners(), "manners: what you threw is left alone, no shot past a player");
        assertTrue(d.recoverDrops());
        assertTrue(d.feedOwner());
        assertTrue(d.offerTools());
        assertTrue(d.partner());
        assertTrue(d.guardPerimeter());
        assertTrue(d.fightPlayers(), "only players you name, or who attack first");
        assertTrue(d.huntDrops());
        assertFalse(d.liveTracking(), "a hunt uses where it last saw them unless you allow the live position");
        assertTrue(d.replantTrees(), "leaving no trace: a sapling where it felled a tree");
        assertTrue(d.putOutFires(), "fire near it put out, a fireplace left");
        assertTrue(d.pickBerries(), "berries picked, the bush left");
        assertTrue(d.lavaFuel(), "lava buckets for big smelts");
        assertTrue(d.keepPhrases(), "learned phrases kept");
        assertTrue(d.talkSummary(), "a running summary of talks");
        assertTrue(d.fillHoles(), "holes near home filled");
        assertTrue(d.huntMonsters(), "monsters hunted for an order's drops");
        assertTrue(d.gearGoal(), "iron armour made where the danger grows");
        assertTrue(d.bowAtRange(), "the bow at range is on by default");
        assertTrue(d.kitGoal(), "the kit goal is on by default");
        assertTrue(d.readChestLabels(), "reading chest labels is on by default");
        assertTrue(d.fillCraters(), "filling creeper holes is on by default");
        assertTrue(d.tradesInPlans(), "trades in plans are on by default");
        assertTrue(d.branchForOre(), "branch mining for ore is on by default");
        assertTrue(d.golemIron(), "golem iron is on by default");
        assertTrue(d.weighFights(), "weighing fights is on by default");
        assertTrue(d.tradingMastery(), "trading mastery is on by default");
        assertTrue(d.wholeTrees(), "felling whole trees is on by default");
        assertTrue(d.hoard(), "hoarding is on by default");
        assertTrue(d.godGear(), "god-tier gear in free time is on by default");
        assertTrue(d.dumpJunk(), "throwing junk away is on by default");
        assertTrue(d.grindXp(), "it hunts for levels once it is doing well");
        assertTrue(d.watchChests(), "it watches its own chests");
        assertFalse(d.revengeThieves(), "revenge is off unless asked");
        assertTrue(d.autoRespawn(), "it comes back by itself");
        assertTrue(d.afkImprove(), "its owner asked for it");
        assertTrue(d.takeTrapTnt(), "its owner asked for it");
        assertTrue(d.nightStopsWork(), "digging in at night stops its work by default");
        // A file saved with the earlier default ("afkGuard": true, and "afkKeepWorking") has no say over the new ones.
        Path dir = Files.createTempDirectory("mineagent-config");
        Files.writeString(dir.resolve("mineagent.json"),
                "{\"behaviour\":{\"afkGuard\":true,\"afkKeepWorking\":false,\"afkStayWithin\":9999}}");
        MineAgentConfig config = MineAgentConfig.load(dir);
        assertFalse(config.behaviour().afkGuardMe());
        assertEquals(256, config.behaviour().afkStayWithin(), "kept in range");
    }
}
