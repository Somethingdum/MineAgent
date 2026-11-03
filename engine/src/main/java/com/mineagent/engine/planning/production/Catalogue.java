package com.mineagent.engine.planning.production;

import com.mineagent.engine.planning.production.Production.Kind;
import com.mineagent.engine.planning.production.Production.Recipe;
import com.mineagent.engine.planning.production.Production.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the game's data says that a plan must not take literally (SMH plan 70, MA7.3, the catalogue). Pure, so it is
 * tested without the game.
 */
public final class Catalogue {
    private Catalogue() {}

    /**
     * The sources left once storage blocks are out: a block that drops itself, is packed from 4 or 9 of one item and
     * unpacks into that item again (an iron block, a coal block, a hay bale). People build those; nature doesn't, so
     * there is none to go and find, and a plan that mines one for its ingots is mining someone's base. Read from the
     * recipes, so a mod's storage blocks are caught too.
     */
    public static Map<String, List<Source>> withoutStorageBlocks(Map<String, List<Recipe>> recipes, Map<String, List<Source>> sources) {
        Map<String, List<Source>> out = new HashMap<>();
        for (var e : sources.entrySet()) {
            List<Source> kept = new ArrayList<>();
            for (Source s : e.getValue()) if (!storage(recipes, s)) kept.add(s);
            if (!kept.isEmpty()) out.put(e.getKey(), kept);
        }
        return out;
    }

    static boolean storage(Map<String, List<Recipe>> recipes, Source s) {
        String block = s.block();
        if (!block.equals(s.item())) return false; // it drops something else (clay balls from clay): found in nature
        for (Recipe pack : recipes.getOrDefault(block, List.of())) {
            if (pack.kind() != Kind.CRAFT || pack.needs().size() != 1) continue;
            int n = pack.needs().get(0).count();
            if (n != 4 && n != 9) continue;
            for (String part : pack.needs().get(0).anyOf()) {
                for (Recipe unpack : recipes.getOrDefault(part, List.of())) {
                    if (unpack.kind() == Kind.CRAFT && unpack.needs().size() == 1 && unpack.needs().get(0).anyOf().contains(block)) return true;
                }
            }
        }
        return false;
    }
}
