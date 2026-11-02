package com.mineagent.engine.skill;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA2.0: a skill's places become bindings, so it can be used in every world. */
final class SkillBindingsLiftTest {
    private static JsonArray steps(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    @Test void coordinatesBecomeBindingsThatResolveBackToThem() {
        JsonArray original = steps("[{\"tool\":\"goto\",\"args\":{\"x\":10,\"y\":64,\"z\":-5}},"
                + "{\"tool\":\"place\",\"args\":{\"block\":\"stone\",\"target_pos\":{\"x\":1,\"y\":2,\"z\":3}}}]");
        SkillBindings.Lifted lifted = SkillBindings.lift(original);
        assertFalse(lifted.worldBound());
        assertFalse(lifted.steps().toString().contains("\"x\":10"));
        var bindings = SkillBindings.defaults(lifted.steps());
        for (int i = 0; i < original.size(); i++) {
            var step = lifted.steps().get(i).getAsJsonObject();
            assertEquals(original.get(i).getAsJsonObject().get("args"), SkillBindings.resolve(step.get("args"), bindings));
        }
        // The input is left as it was, and lifting again changes nothing.
        assertTrue(original.toString().contains("\"x\":10"));
        assertEquals(lifted.steps(), SkillBindings.lift(lifted.steps()).steps());
    }

    @Test void aStepWithNoPlaceIsUnchanged() {
        JsonArray original = steps("[{\"tool\":\"craft_item\",\"args\":{\"item\":\"minecraft:stick\",\"count\":4}}]");
        SkillBindings.Lifted lifted = SkillBindings.lift(original);
        assertEquals(original, lifted.steps());
        assertFalse(lifted.worldBound());
    }

    @Test void aRawStepNamingAPlaceKeepsTheSkillInItsWorld() {
        assertTrue(SkillBindings.lift(steps("[{\"tool\":\"mine_block\",\"args_raw\":\"10, 64, -5\"}]")).worldBound());
        assertFalse(SkillBindings.lift(steps("[{\"tool\":\"mine_block\",\"args_raw\":\"the oak\"}]")).worldBound());
    }
}
