package com.mineagent.engine.world;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA10.2: "don't touch my garden" keeps every companion out of it. */
final class ProtectedAreasTest {
    @Test void insideTheRadiusInItsDimensionOnly() {
        ProtectedAreas.clearAll();
        UUID c = UUID.randomUUID();
        ProtectedAreas.set(c, List.of(new ProtectedAreas.Area("garden", "minecraft:overworld", 10, 64, 10, ProtectedAreas.RADIUS)));
        assertTrue(ProtectedAreas.protects("minecraft:overworld", 12, 64, 13));
        assertEquals("garden", ProtectedAreas.at("minecraft:overworld", 10, 70, 10).name());
        assertFalse(ProtectedAreas.protects("minecraft:overworld", 19, 64, 10), "9 blocks off");
        assertFalse(ProtectedAreas.protects("minecraft:the_nether", 10, 64, 10));
        ProtectedAreas.set(c, List.of());
        assertFalse(ProtectedAreas.protects("minecraft:overworld", 10, 64, 10), "taken back");
    }
}
