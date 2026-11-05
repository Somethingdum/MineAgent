package com.mineagent.engine.alto;

import com.mineagent.engine.alto.tasks.life.ModelWants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The model's next task in Voyager's words ("Mine 3 iron ore", "Craft a stone pickaxe") read for AltoClef's catalogue. */
final class ModelWantsWordsTest {
    @Test void voyagersExamplesAreRead() {
        assertEquals(new ModelWants.Words("mine", 3, "iron ore"), ModelWants.split("Mine 3 iron ore"));
        assertEquals(new ModelWants.Words("craft", 1, "stone pickaxe"), ModelWants.split("Craft a stone pickaxe"));
        assertEquals(new ModelWants.Words("smelt", 4, "iron ingots"), ModelWants.split("Smelt 4 iron ingots."));
        assertEquals(new ModelWants.Words("kill", 1, "zombie"), ModelWants.split("Kill 1 zombie"));
        assertEquals(new ModelWants.Words("obtain", 1, "wood log"), ModelWants.split("Obtain a wood log"));
        assertEquals(new ModelWants.Words("cook", 2, "beef"), ModelWants.split("  Cook   two beef "));
        assertEquals(new ModelWants.Words("craft", 1, "furnace"), ModelWants.split("Craft furnace"));
    }

    @Test void whatIsNoTaskIsNotRead() {
        assertNull(ModelWants.split(null));
        assertNull(ModelWants.split("Explore the area to the north"));
        assertNull(ModelWants.split("Build a castle"));
        assertNull(ModelWants.split("mine"));
    }

    @Test void countsAreKeptInBounds() {
        assertEquals(64, ModelWants.split("Mine 500 cobblestone").count());
        assertEquals(1, ModelWants.split("Mine 0 cobblestone").count());
    }
}
