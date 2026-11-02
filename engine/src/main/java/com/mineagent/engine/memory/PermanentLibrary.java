package com.mineagent.engine.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mineagent.engine.skill.SkillBindings;
import com.mineagent.engine.skill.SkillLibrary;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * The companion's permanent library (SMH plan 69, MA2.0; the user, 2026-11-02: "I do need everything it's
 * learned in terms of SKILLS to be permanent. While world specific knowledge is limited to a save file.").
 *
 * <p>Skills, general lessons and mechanism knowledge live in
 * {@code <configDir>/mineagent/library/<ownerUuid>_<companionName>/} and are used in every world. Places,
 * chests, plans, the world model, conversations and world goals stay in the world's own memory folder.
 * Mechanism rules already carry the game's fingerprint and go stale where it differs
 * (MechanismKnowledgeBase), so a library shared by worlds with other mods never applies them there.
 * {@link MemoryPersistence} writes the files atomically and quarantines a corrupt one, as it does its own.
 */
public final class PermanentLibrary {
    public static final String SKILLS = "skills.json";
    public static final String LESSONS = "lessons.json";
    public static final String MECHANISMS = "mechanisms.json";

    /** x, y, z written out: a lesson or a raw step that names a place. */
    private static final Pattern COORDINATE = Pattern.compile("-?\\d+,\\s*-?\\d+,\\s*-?\\d+");

    private PermanentLibrary() {}

    /** The library root under the mod's config folder. */
    public static Path root(Path configDir) {
        return configDir.resolve("mineagent").resolve("library");
    }

    /** Whether text names a coordinate, so it belongs to one world. */
    public static boolean namesCoordinate(String text) {
        return text != null && COORDINATE.matcher(text).find();
    }

    /**
     * Whether a lesson goes to the library: applicability {@code general} (or none given), unless the lesson
     * or its context names a coordinate. Any other applicability is about one world and stays there.
     */
    public static boolean isGeneralLesson(ReflectionSystem.Reflection r) {
        if (r == null) return false;
        String applies = r.applicability() == null ? "" : r.applicability().strip();
        if (!applies.isEmpty() && !applies.equalsIgnoreCase("general")) return false;
        return !namesCoordinate(r.lesson()) && !namesCoordinate(r.context()) && !namesCoordinate(r.trigger());
    }

    /**
     * The skill as the library keeps it, its coordinates made bindings (SkillBindings.lift); null when a
     * coordinate cannot be lifted, so it stays in its world.
     */
    public static SkillLibrary.Skill portable(SkillLibrary.Skill skill) {
        if (skill == null || skill.actionSequence() == null) return null;
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(skill.actionSequence());
        } catch (RuntimeException notJson) {
            return namesCoordinate(skill.actionSequence()) ? null : skill;
        }
        if (!parsed.isJsonArray()) return namesCoordinate(skill.actionSequence()) ? null : skill;
        SkillBindings.Lifted lifted;
        try {
            lifted = SkillBindings.lift((JsonArray) parsed);
        } catch (RuntimeException invalidBindings) {
            return null;
        }
        if (lifted.worldBound()) return null;
        return new SkillLibrary.Skill(skill.name(), skill.description(), skill.triggerCondition(),
                lifted.steps().toString(), skill.successRate(), skill.invocations(), skill.createdTick());
    }
}
