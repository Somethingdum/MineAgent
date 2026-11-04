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

    @Test void theLadderGoesToDiamondThenTheBestBooks() {
        java.util.Map<String, GodGear.Piece> pieces = new java.util.HashMap<>();
        pieces.put("sword", new GodGear.Piece("sword", 3, Map.of()));
        java.util.function.Predicate<String> diamonds = id -> id.equals("minecraft:diamond_sword");
        var n = GodGear.next(pieces, Map.of(), Map.of(), diamonds, java.util.Set.of());
        assertEquals(GodGear.Move.MAKE, n.move());
        assertEquals("minecraft:diamond_sword", n.what(), "iron first goes up to diamond");
        assertNull(GodGear.next(pieces, Map.of("sharpness", 5), Map.of(), id -> false, java.util.Set.of()),
                "no books go on an iron sword, and nothing can be made");
        pieces.put("sword", new GodGear.Piece("sword", 4, Map.of("sharpness", 5)));
        var books = GodGear.next(pieces, Map.of("unbreaking", 3, "protection", 4), Map.of(), id -> false, java.util.Set.of());
        assertEquals(GodGear.Move.APPLY_BOOKS, books.move(), "Unbreaking III it carries goes on the diamond sword");
        assertEquals("sword", books.kind());
        var buy = GodGear.next(pieces, Map.of("unbreaking", 2), Map.of("mending", 1, "looting", 2), id -> false, java.util.Set.of());
        assertEquals(GodGear.Move.BUY_BOOK, buy.move(), "a book at too low a level isn't applied; Mending its keeper sells is bought");
        assertEquals("mending", buy.what());
        var after = GodGear.next(pieces, Map.of(), Map.of("mending", 1), id -> false, java.util.Set.of(buy.key()));
        assertNull(after, "a buy that failed a while ago isn't tried again at once, and Looting II isn't the best");
        pieces.put("pickaxe", new GodGear.Piece("pickaxe", 2, Map.of()));
        var pick = GodGear.next(pieces, Map.of(), Map.of(), id -> id.equals("minecraft:iron_pickaxe"), java.util.Set.of());
        assertEquals("minecraft:iron_pickaxe", pick.what(), "no diamonds: iron at least");
        assertEquals("minecraft:shield", GodGear.next(java.util.Map.of(), Map.of(), Map.of(), id -> id.equals("minecraft:shield"),
                java.util.Set.of()).what(), "an untiered kind it hasn't got is made");
        assertEquals(5, GodGear.tier("minecraft:netherite_sword"));
        assertEquals(2, GodGear.tier("minecraft:chainmail_helmet"));
    }

    @Test void diamondGoesToNetheriteWhenItHasTheTemplateAndAnIngot() {
        java.util.Map<String, GodGear.Piece> pieces = new java.util.HashMap<>();
        pieces.put("sword", new GodGear.Piece("sword", 4, Map.of("sharpness", 5)));
        var up = GodGear.next(pieces, Map.of("unbreaking", 3), Map.of(), id -> false, k -> k.equals("sword"), java.util.Set.of());
        assertEquals(GodGear.Move.SMITH, up.move(), "netherite first: the books go with it");
        assertEquals("minecraft:netherite_sword", up.what());
        var books = GodGear.next(pieces, Map.of("unbreaking", 3), Map.of(), id -> false, k -> false, java.util.Set.of());
        assertEquals(GodGear.Move.APPLY_BOOKS, books.move(), "no template or ingot: the books instead");
        pieces.put("sword", new GodGear.Piece("sword", 5, Map.of("sharpness", 5)));
        var done = GodGear.next(pieces, Map.of(), Map.of(), id -> false, k -> true, java.util.Set.of());
        assertNull(done, "netherite already, and nothing else to do for it");
        assertNull(GodGear.next(java.util.Map.of("bow", new GodGear.Piece("bow", 4, Map.of("power", 5, "unbreaking", 3, "mending", 1, "punch", 2, "flame", 1))),
                Map.of(), Map.of(), id -> false, k -> true, java.util.Set.of()), "a bow has no netherite");
    }
}

