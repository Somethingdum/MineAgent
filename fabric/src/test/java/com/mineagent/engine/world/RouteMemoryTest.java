package com.mineagent.engine.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Places, the trail of a trip, and routes between places (SMH plan 70, MA5.6). */
final class RouteMemoryTest {
    private static RouteMemory.Point p(int x, int y, int z) {
        return new RouteMemory.Point("minecraft:overworld", x, y, z);
    }

    @Test void homeIsWhereYouSayItIs() {
        RouteMemory m = new RouteMemory();
        m.name("home", null, p(0, 64, 0), 1);
        m.name("home", null, p(100, 64, 100), 2);
        assertEquals(1L, m.places().stream().filter(x -> x.kind().equals("home")).count(), "one home");
        assertEquals(p(100, 64, 100), m.find("home", p(0, 64, 0)).orElseThrow().at());
        assertEquals(p(100, 64, 100), m.find("my base", p(0, 64, 0)).orElseThrow().at(), "base means home");
        m.name("named", "the mine", p(30, 12, -40), 3);
        assertEquals(p(30, 12, -40), m.find("mine", null).orElseThrow().at());
        assertTrue(m.find("the farm", null).isEmpty());
    }

    @Test void aVillageWalkedIntoTwiceIsOneVillage() {
        RouteMemory m = new RouteMemory();
        var first = m.found("village", p(500, 70, 500), 1);
        var again = m.found("village", p(530, 70, 520), 2);
        assertEquals(first.id(), again.id());
        var other = m.found("village", p(2000, 70, 0), 3);
        assertNotEquals(first.id(), other.id());
        assertEquals("the village 2", other.name());
        assertEquals(p(500, 70, 500), m.find("village", p(520, 70, 500)).orElseThrow().at(), "the nearest of a kind");
    }

    @Test void theTrailKeepsAPointEveryEightBlocksInTheOpenAndEveryTurnUnderground() {
        RouteMemory m = new RouteMemory();
        for (int x = 0; x <= 40; x++) m.at(p(x, 64, 0), false);
        assertEquals(6, m.wayBack().size(), "0, 8, 16, 24, 32, 40");
        RouteMemory cave = new RouteMemory();
        cave.at(p(0, 30, 0), true);
        cave.at(p(3, 30, 0), true);
        cave.at(p(3, 30, 3), true);   // a turn
        cave.at(p(3, 28, 5), true);   // down
        assertEquals(4, cave.wayBack().size());
        assertEquals(p(0, 30, 0), cave.wayBack().get(3), "the way back ends where it began");
    }

    @Test void aTripBetweenTwoPlacesBecomesARouteAndRoutesChain() {
        RouteMemory m = new RouteMemory();
        var home = m.name("home", null, p(0, 64, 0), 1);
        var mine = m.name("named", "the mine", p(80, 64, 0), 1);
        var farm = m.name("named", "the farm", p(80, 64, 80), 1);
        m.startTrip(home.id(), p(0, 64, 0));
        for (int x = 0; x <= 80; x++) m.at(p(x, 64, 0), false);
        assertTrue(m.endTrip(p(80, 64, 0), 10).isPresent());
        m.startTrip(mine.id(), p(80, 64, 0));
        for (int z = 0; z <= 80; z++) m.at(p(80, 64, z), false);
        assertTrue(m.endTrip(p(80, 64, 80), 20).isPresent());
        List<RouteMemory.Point> way = m.way(home.id(), farm.id());
        assertFalse(way.isEmpty());
        assertEquals(p(0, 64, 0), way.get(0));
        assertEquals(p(80, 64, 80), way.get(way.size() - 1));
        List<RouteMemory.Point> back = m.way(farm.id(), home.id());
        assertEquals(p(0, 64, 0), back.get(back.size() - 1), "routes walk either way");
    }

    @Test void aRouteThatKeepsFailingIsAvoided() {
        RouteMemory m = new RouteMemory();
        var a = m.name("named", "a", p(0, 64, 0), 1);
        var b = m.name("named", "b", p(40, 64, 0), 1);
        m.startTrip(a.id(), p(0, 64, 0));
        for (int x = 0; x <= 40; x++) m.at(p(x, 64, 0), false);
        m.endTrip(p(40, 64, 0), 5);
        assertFalse(m.way(a.id(), b.id()).isEmpty());
        m.routeFailed(a.id(), b.id());
        m.routeFailed(a.id(), b.id());
        assertTrue(m.way(a.id(), b.id()).isEmpty(), "1 success in 3 tries");
    }

    @Test void simplifyingKeepsTheTurns() {
        List<RouteMemory.Point> line = new java.util.ArrayList<>();
        for (int x = 0; x <= 10; x++) line.add(p(x, 64, 0));
        for (int z = 1; z <= 10; z++) line.add(p(10, 64, z));
        var simple = RouteMemory.simplify(line);
        assertTrue(simple.contains(p(10, 64, 0)), "the corner stays");
        assertTrue(simple.size() < line.size());
        assertEquals(p(10, 64, 10), simple.get(simple.size() - 1));
    }

    @Test void whatIsSavedComesBack() {
        RouteMemory m = new RouteMemory();
        m.name("home", null, p(1, 2, 3), 1);
        m.at(p(1, 2, 3), false);
        RouteMemory back = new RouteMemory();
        back.importState(m.exportState());
        assertTrue(back.find("home", null).isPresent());
        assertEquals(1, back.wayBack().size());
        var next = back.name("named", "x", p(9, 9, 9), 2);
        assertNotEquals(back.find("home", null).orElseThrow().id(), next.id(), "ids go on, not over");
    }

    @Test void walkingFromHomeToTheMineMakesTheRouteOnItsOwn() {
        RouteMemory m = new RouteMemory();
        var home = m.name("home", null, p(0, 64, 0), 1);
        var mine = m.name("named", "the mine", p(60, 64, 0), 1);
        for (int x = 0; x <= 60; x++) m.walked(p(x, 64, 0), false, x);
        assertEquals(1, m.routes().size());
        assertEquals(home.id(), m.routes().get(0).from());
        assertEquals(mine.id(), m.routes().get(0).to());
        assertEquals(mine.id(), m.placeAt(p(58, 64, 1)).orElseThrow().id());
    }

    @Test void tripsAreKeptAndEachPartKnowsWhenItChanged() {
        RouteMemory m = new RouteMemory();
        long[] before = m.versions();
        var home = m.name("home", null, p(0, 64, 0), 1);
        assertTrue(m.versions()[0] > before[0], "naming a place changes places");
        m.name("named", "the mine", p(60, 64, 0), 1);
        long[] named = m.versions();
        for (int x = 0; x <= 60; x++) m.walked(p(x, 64, 0), false, x);
        assertTrue(m.versions()[1] > named[1], "walking changes the trail");
        assertEquals(1, m.trips().size());
        assertEquals(home.id(), m.trips().get(0).from());
        RouteMemory back = new RouteMemory();
        back.importPlaces(m.exportPlaces());
        back.importTrail(m.exportTrail());
        back.importTrips(m.trips());
        assertEquals(m.places().size(), back.places().size());
        assertEquals(1, back.routes().size());
        assertEquals(1, back.trips().size());
    }

    @Test void aPortalItWentThroughIsLinkedBothWaysAndKept() {
        RouteMemory m = new RouteMemory();
        var ow = new RouteMemory.Point("minecraft:overworld", 100, 64, 100);
        var nether = new RouteMemory.Point("minecraft:the_nether", 12, 70, 12);
        assertTrue(m.portalTo("minecraft:the_nether", ow).isEmpty(), "no link before it has been through");
        m.linkPortal(ow, nether, 50);
        assertEquals(nether, m.portalTo("minecraft:the_nether", new RouteMemory.Point("minecraft:overworld", 90, 64, 90)).orElseThrow().to());
        assertEquals(ow, m.portalTo("minecraft:overworld", nether).orElseThrow().to());
        assertEquals(2, m.places().stream().filter(p -> "portal".equals(p.kind())).count());
        RouteMemory back = new RouteMemory();
        back.importPlaces(m.exportPlaces());
        assertTrue(back.portalTo("minecraft:the_nether", ow).isPresent());
    }
}
