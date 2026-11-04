package com.mineagent.engine.gear;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GodGearTest {
    @Test void everyPieceCanTakeItsWholeBestSetWithNoStepTooExpensive() {
        for (var e : GodGear.BEST.entrySet()) {
            var o = GodGear.order(e.getValue());
            assertTrue(o.possible(), e.getKey() + " can't be done under 40 a step");
            for (var s : o.steps()) assertTrue(s.cost() < GodGear.TOO_EXPENSIVE, e.getKey() + ": " + s);
            assertEquals(e.getValue().size(), o.steps().size(), "one step per book");
        }
    }

    @Test void theOrderIsCheaperThanOneBookAtATime() {
        var books = GodGear.BEST.get("sword");
        var best = GodGear.order(books);
        // One at a time onto the item: each step's penalty doubles; by the sixth it is past the limit or near it.
        int naive = 0, work = 0;
        boolean naiveOk = true;
        for (var b : books) {
            int cost = (1 << work) - 1 + b.level() * b.bookCost();
            if (cost >= GodGear.TOO_EXPENSIVE) naiveOk = false;
            naive += cost;
            work++;
        }
        assertTrue(best.total() < naive || !naiveOk, "best " + best.total() + " vs one by one " + naive);
    }

    @Test void whatAPieceLacksAndItsKind() {
        assertEquals("boots", GodGear.kindOf("minecraft:netherite_boots"));
        assertEquals("axe", GodGear.kindOf("minecraft:diamond_axe"));
        assertEquals("pickaxe", GodGear.kindOf("minecraft:iron_pickaxe"));
        var lacks = GodGear.missing("chestplate", Map.of("minecraft:protection".replace("minecraft:", ""), 4, "unbreaking", 2));
        assertEquals(List.of("unbreaking", "mending"), lacks.stream().map(GodGear.Ench::id).toList());
    }
}
