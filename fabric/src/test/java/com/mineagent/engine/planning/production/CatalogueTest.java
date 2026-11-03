package com.mineagent.engine.planning.production;

import com.mineagent.engine.planning.production.Production.Kind;
import com.mineagent.engine.planning.production.Production.Need;
import com.mineagent.engine.planning.production.Production.Recipe;
import com.mineagent.engine.planning.production.Production.Source;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The catalogue (SMH plan 70, MA7.3): storage blocks are built, not found. */
final class CatalogueTest {
    static String m(String id) {
        return "minecraft:" + id;
    }

    @Test void storageBlocksAreNotThereToBeMined() {
        Map<String, List<Recipe>> recipes = Map.of(
                m("iron_block"), List.of(new Recipe(m("iron_block"), Kind.CRAFT, m("iron_block"), 1, List.of(new Need(List.of(m("iron_ingot")), 9)), true, 0)),
                m("iron_ingot"), List.of(new Recipe(m("iron_ingot_from_iron_block"), Kind.CRAFT, m("iron_ingot"), 9,
                        List.of(new Need(List.of(m("iron_block")), 1)), false, 0)),
                m("clay"), List.of(new Recipe(m("clay"), Kind.CRAFT, m("clay"), 1, List.of(new Need(List.of(m("clay_ball")), 4)), false, 0)),
                m("stone_bricks"), List.of(new Recipe(m("stone_bricks"), Kind.CRAFT, m("stone_bricks"), 4, List.of(new Need(List.of(m("stone")), 4)), false, 0)));
        Map<String, List<Source>> sources = Map.of(
                m("iron_block"), List.of(new Source(m("iron_block"), m("iron_block"), "pickaxe", 2, false, 1.0, 34)),
                m("raw_iron"), List.of(new Source(m("raw_iron"), m("iron_ore"), "pickaxe", 2, false, 1.0, 23)),
                m("clay_ball"), List.of(new Source(m("clay_ball"), m("clay"), "", 0, false, 4.0, 18)),
                m("stone_bricks"), List.of(new Source(m("stone_bricks"), m("stone_bricks"), "pickaxe", 1, false, 1.0, 34)));
        var kept = Catalogue.withoutStorageBlocks(recipes, sources);
        assertFalse(kept.containsKey(m("iron_block")), "an iron block is someone's");
        assertTrue(kept.containsKey(m("raw_iron")), "iron ore is found");
        assertTrue(kept.containsKey(m("clay_ball")), "clay drops clay balls: found in nature");
        assertTrue(kept.containsKey(m("stone_bricks")), "stone bricks don't unpack into stone: not storage");
    }
}
