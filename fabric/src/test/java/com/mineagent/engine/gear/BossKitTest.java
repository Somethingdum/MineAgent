package com.mineagent.engine.gear;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BossKitTest {
    @Test void whichBoss() {
        assertEquals("dragon", BossKit.boss("get ready for the ender dragon"));
        assertEquals("wither", BossKit.boss("prepare for the wither"));
        assertNull(BossKit.boss("kill the wither skeletons"));
    }

    @Test void onlyWhatIsShort() {
        var shortOf = BossKit.short_("dragon", Map.of("minecraft:arrow", 64, "minecraft:bow", 1, "minecraft:water_bucket", 1));
        assertTrue(shortOf.stream().anyMatch(w -> w.item().equals("minecraft:arrow") && w.count() == 128));
        assertTrue(shortOf.stream().noneMatch(w -> w.item().equals("minecraft:bow")));
        assertTrue(BossKit.short_("wither", Map.of()).stream().anyMatch(w -> w.item().equals("minecraft:milk_bucket")), "milk takes the wither off");
        assertTrue(BossKit.brews("dragon").stream().anyMatch(b -> b.potion().equals("slow falling")));
    }
}
