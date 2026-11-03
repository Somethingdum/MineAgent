package com.mineagent.engine.skill;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Skills chosen by need (SMH plan 70, MA7.7): by the tools a job needs, then what they name, within a budget. */
final class SkillChoiceTest {
    static final String[] TOOLS = {"auto_mine", "craft", "goto", "transfer_items", "melee_attack", "build", "use_item"};

    static SkillLibrary library() {
        SkillLibrary lib = new SkillLibrary();
        for (int i = 0; i < 400; i++) {
            String tool = TOOLS[i % TOOLS.length];
            lib.registerSequence("skill_" + i, "a learned way number " + i + " to do things with " + tool,
                    "when it fits " + i, "[{\"tool\":\"" + tool + "\",\"args\":{}}]", true);
        }
        // A mining skill that needs a pickaxe made first, by another skill it names.
        lib.registerSequence("make_stone_pickaxe", "craft a stone pickaxe from cobblestone", "no pickaxe",
                "[{\"tool\":\"craft\",\"args\":{}}]", true);
        lib.registerSequence("branch_mine_iron", "mine iron ore at Y 16; first make_stone_pickaxe if it has none", "iron needed",
                "[{\"tool\":\"goto\",\"args\":{}},{\"tool\":\"auto_mine\",\"args\":{}}]", true);
        return lib;
    }

    @Test void theToolsTheJobNeedsComeFirst() {
        var lib = library();
        assertEquals(402, lib.size());
        assertTrue(SkillLibrary.capabilities("mine some iron").contains("auto_mine"));
        var chosen = lib.forNeeds("mine some iron", Set.of("auto_mine", "goto"), 6, SkillLibrary.OFFER_BUDGET);
        assertFalse(chosen.isEmpty());
        assertEquals("branch_mine_iron", chosen.get(0).name(), "it uses both tools the job needs");
        assertTrue(chosen.stream().anyMatch(s -> s.name().equals("make_stone_pickaxe")), "what it names comes with it: " + chosen);
        for (var s : chosen) {
            boolean usesNeeded = SkillLibrary.toolsOf(s).stream().anyMatch(t -> t.equals("auto_mine") || t.equals("goto"));
            assertTrue(usesNeeded || s.name().equals("make_stone_pickaxe"), s.name() + " is not for this job");
        }
    }

    @Test void aLibraryOfHundredsStaysWithinItsBudget() {
        var lib = library();
        int budget = 400;
        var chosen = lib.forNeeds("craft things and go places", null, 50, budget);
        int chars = 0;
        for (var s : chosen) chars += s.name().length() + s.description().length() + s.triggerCondition().length();
        assertTrue(chars <= budget, chars + " characters for a budget of " + budget);
        assertTrue(chosen.size() <= 50);
        assertTrue(chosen.size() >= 3, "it still offers some: " + chosen.size());
        assertTrue(lib.forNeeds("anything", null, 0, budget).isEmpty());
    }
}
