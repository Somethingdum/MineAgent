package com.mineagent.engine.memory;

import com.mineagent.api.llm.ChatMessage;
import com.mineagent.engine.cognition.CognitiveState;
import com.mineagent.engine.exploration.MechanismExplorer;
import com.mineagent.engine.planning.HierarchicalRollingPlanner;
import com.mineagent.engine.planning.PlanGraph;
import com.mineagent.engine.planning.ResourceReservationLedger;
import com.mineagent.engine.skill.SkillLibrary;
import com.mineagent.engine.world.ActionTransactionLedger;
import com.mineagent.engine.world.BeliefState;
import com.mineagent.engine.world.SemanticWorldModel;
import com.mineagent.engine.world.WorldAssetIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA2.0: skills and general lessons are kept for every world; world knowledge stays in its save. */
final class PermanentLibraryTest {
    private static final String KEY = "00000000-0000-0000-0000-000000000001_Steve";

    /** One companion's memory in one world, with the library under {@code config} (none: null). */
    private static final class Memory {
        final SkillLibrary skills = new SkillLibrary();
        final WorldAssetIndex assets = new WorldAssetIndex();
        final ReflectionSystem reflections = new ReflectionSystem();
        final MemoryPersistence persistence;

        Memory(Path world, Path config) {
            SemanticWorldModel semantic = new SemanticWorldModel();
            PlanGraph plan = new PlanGraph();
            persistence = new MemoryPersistence(world.resolve("mineagent_memory").resolve(KEY),
                    new CognitiveMap(), new PlaceEventMemory(), new ImportanceEvaluator(), reflections,
                    plan, new BeliefState(), assets, new ExperienceStore(), skills, semantic,
                    new HierarchicalRollingPlanner(plan, semantic),
                    new MechanismExplorer(semantic, (experiment, supported, evidence, tick) -> { }),
                    new ArrayList<ChatMessage>(), new CognitiveState(), new LayeredMemory(),
                    new ActionTransactionLedger(), new ResourceReservationLedger());
            if (config != null) persistence.setLibraryRoot(PermanentLibrary.root(config));
            persistence.loadAll();
        }
    }

    private static final String PORTABLE = "[{\"tool\":\"craft_item\",\"args\":{\"item\":\"minecraft:crafting_table\",\"count\":1}}]";
    private static final String LIFTABLE = "[{\"tool\":\"mine_block\",\"args\":{\"x\":10,\"y\":64,\"z\":-5}}]";
    private static final String BOUND = "[{\"tool\":\"mine_block\",\"args_raw\":\"dig at 10, 64, -5\"}]";

    @Test void aSkillLearnedInOneWorldIsFoundInAnotherAndOutlivesIt(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("config");
        Path worldA = tmp.resolve("worldA");
        Memory a = new Memory(worldA, config);
        a.skills.register("craft_table", "make a crafting table", "need a table", PORTABLE, true);
        a.skills.register("mine_ore", "mine the ore", "ore here", LIFTABLE, true);
        a.skills.register("dig_here", "dig at one spot", "here", BOUND, true);
        a.persistence.saveAll();

        Memory b = new Memory(tmp.resolve("worldB"), config);
        assertNotNull(b.skills.getActionSequence("craft_table"));
        // Its coordinates are bindings now, with the old place as their default.
        String lifted = b.skills.getActionSequence("mine_ore");
        assertNotNull(lifted);
        assertTrue(lifted.contains("$binding"), lifted);
        // A skill whose place cannot be lifted stays in its own world.
        assertNull(b.skills.getActionSequence("dig_here"));
        assertNotNull(new Memory(worldA, config).skills.getActionSequence("dig_here"));

        // World A gone: the library keeps what was learned there.
        try (var walk = Files.walk(worldA)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
        assertFalse(Files.exists(worldA));
        assertNotNull(new Memory(tmp.resolve("worldC"), config).skills.getActionSequence("craft_table"));
    }

    @Test void aChestRecordedInOneWorldIsAbsentInAnother(@TempDir Path tmp) {
        Path config = tmp.resolve("config");
        Memory a = new Memory(tmp.resolve("worldA"), config);
        a.assets.observeContainer("chest", new WorldAssetIndex.Position("minecraft:overworld", 1, 64, 2),
                List.of(new WorldAssetIndex.ItemObservation(0, "minecraft:diamond", 3, 0, 0, Set.of(), 1.0)), true, 0L);
        assertFalse(a.assets.exportState().assets().isEmpty());
        a.persistence.saveAll();
        assertTrue(new Memory(tmp.resolve("worldB"), config).assets.exportState().assets().isEmpty());
        assertFalse(new Memory(tmp.resolve("worldA"), config).assets.exportState().assets().isEmpty());
    }

    @Test void generalLessonsTravelAndLessonsAboutAPlaceStay(@TempDir Path tmp) {
        Path config = tmp.resolve("config");
        Memory a = new Memory(tmp.resolve("worldA"), config);
        a.reflections.importAll(List.of(
                new ReflectionSystem.Reflection("lesson", "death", "fell into lava", "Carry a water bucket before mining near lava.", 1L, "general"),
                new ReflectionSystem.Reflection("lesson", "death", "the cave at 120, 30, -44", "That cave floods.", 2L, "general"),
                new ReflectionSystem.Reflection("lesson", "failure", "the base", "The base chest is full.", 3L, "world")), new HashMap<>());
        a.persistence.saveAll();
        List<String> b = new Memory(tmp.resolve("worldB"), config).reflections.exportAll().stream().map(ReflectionSystem.Reflection::lesson).toList();
        assertEquals(List.of("Carry a water bucket before mining near lava."), b);
        assertEquals(3, new Memory(tmp.resolve("worldA"), config).reflections.exportAll().size());
    }

    @Test void aWorldFromBeforeTheLibraryHasItsSkillsMovedAndItsOldFileKept(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("config");
        Path world = tmp.resolve("worldOld");
        Memory before = new Memory(world, null);
        before.skills.register("craft_table", "make a crafting table", "need a table", PORTABLE, true);
        before.persistence.saveAll();
        Path dir = world.resolve("mineagent_memory").resolve(KEY);
        assertTrue(Files.exists(dir.resolve("learned_skills.json")));

        Memory after = new Memory(world, config);
        assertNotNull(after.skills.getActionSequence("craft_table"));
        assertFalse(Files.exists(dir.resolve("learned_skills.json")));
        assertTrue(Files.exists(dir.resolve("learned_skills.migrated.json")));
        assertTrue(Files.exists(PermanentLibrary.root(config).resolve(KEY).resolve(PermanentLibrary.SKILLS)));
        assertNotNull(new Memory(tmp.resolve("worldNew"), config).skills.getActionSequence("craft_table"));
    }

    @Test void withoutALibraryEverythingStaysInTheWorld(@TempDir Path tmp) {
        Memory a = new Memory(tmp.resolve("worldA"), null);
        a.skills.register("craft_table", "make a crafting table", "need a table", PORTABLE, true);
        a.persistence.saveAll();
        assertNull(new Memory(tmp.resolve("worldB"), null).skills.getActionSequence("craft_table"));
        assertNull(a.persistence.libraryDir());
    }
}
