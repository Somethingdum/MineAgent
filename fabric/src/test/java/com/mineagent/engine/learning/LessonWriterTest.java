package com.mineagent.engine.learning;

import com.mineagent.engine.skill.SkillLibrary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA2.2 and MA2.3: lessons in the model's words, skill write-ups that a review must pass. */
final class LessonWriterTest {
    private static final String[][] SKILLS = { // id, the goal text a plain entry keeps, a write-up, when
            {"s1", "get stone", "Mine cobblestone with a pickaxe from exposed stone nearby", "when stone, cobblestone or rock blocks are needed"},
            {"s2", "make a table", "Craft a crafting table from four planks", "when a crafting table or workbench is needed"},
            {"s3", "food", "Kill a cow or pig and cook the raw meat in a furnace", "when hungry or food, meat or steak is needed"},
            {"s4", "go home", "Walk back to the bed and sleep through the night", "when night falls, to sleep or skip the night"},
            {"s5", "wood", "Chop logs from the nearest tree with an axe", "when logs, wood or planks are needed"}};
    private static final String[][] REQUESTS = {
            {"mine some cobblestone", "s1"}, {"I need rock blocks", "s1"}, {"build a workbench", "s2"}, {"craft a crafting table", "s2"},
            {"I am hungry, get steak", "s3"}, {"cook some meat", "s3"}, {"it is night, sleep", "s4"}, {"skip the night in bed", "s4"},
            {"chop a tree for logs", "s5"}, {"we need planks", "s5"}};

    private static int hits(boolean writtenUp) {
        SkillLibrary lib = new SkillLibrary();
        for (String[] s : SKILLS) {
            lib.register(s[0], writtenUp ? s[2] : s[1], writtenUp ? s[3] : s[1], "[{\"tool\":\"auto_mine\",\"args\":{}}]", true);
        }
        int hits = 0;
        for (String[] q : REQUESTS) {
            var top = lib.relevant(q[0], 1);
            if (!top.isEmpty() && top.get(0).name().equals(q[1])) hits++;
        }
        return hits;
    }

    @Test void writtenUpSkillsAreFoundForMoreRequestsThanPlainOnes() {
        int plain = hits(false);
        int writtenUp = hits(true);
        assertTrue(writtenUp > plain, "written up " + writtenUp + " vs plain " + plain);
        assertTrue(writtenUp >= 8, "written up found " + writtenUp + " of 10");
    }

    @Test void aWriteUpIsCheckedBeforeItIsKept() {
        var up = LessonWriter.parseWriteUp("{\"name\":\"Mine_Stone\",\"description\":\"Mine cobblestone nearby\",\"when\":\"\",\"inputs\":[\"count\"]}").orElseThrow();
        assertEquals("mine_stone", up.name());
        assertEquals(List.of("count"), up.inputs());
        assertTrue(LessonWriter.parseWriteUp("{\"name\":\"no spaces allowed\",\"description\":\"Mine cobblestone\"}").isEmpty());
        assertTrue(LessonWriter.parseReview("{\"ok\":true,\"fix\":\"\"}"));
        assertFalse(LessonWriter.parseReview("{\"ok\":\"true\"}"), "only an explicit true passes");
        assertFalse(LessonWriter.parseReview("{\"ok\":false,\"fix\":\"it also smelts\"}"));
        assertFalse(LessonWriter.parseReview("I think it is fine"));
        var review = LessonWriter.reviewRequest("http://x", "k", "smh/auto", 0.7, up, "[]");
        assertEquals("smh/review", review.model());
        assertTrue(review.tools().isEmpty());
    }

    @Test void aLessonComesBackAsGeneralOrWorldAndIsRecalledForASimilarTask() {
        var lesson = LessonWriter.parseLesson("<think>x</think>{\"lesson\":\"Carry a water bucket when mining near lava.\",\"applies\":\"GENERAL\"}").orElseThrow();
        assertEquals("general", lesson.applies());
        assertEquals("world", LessonWriter.parseLesson("{\"lesson\":\"The chest by the gate is full.\",\"applies\":\"here\"}").orElseThrow().applies());
        assertTrue(LessonWriter.parseLesson("{\"lesson\":\"short\"}").isEmpty());
        var request = LessonWriter.lessonRequest("http://x", "k", "smh/auto", 0.7, "mine iron near lava",
                "died, score 0/100, 20 health lost, 40 s", List.of("auto_mine {}", "result: fell"), "health 0");
        assertEquals("smh/learn", request.model());
        assertTrue(request.tools().isEmpty());
        var reflections = new com.mineagent.engine.memory.ReflectionSystem();
        reflections.recordLesson("mine iron near lava", "died, score 0/100", lesson.text(), lesson.applies());
        assertTrue(reflections.summarizeForPrompt("mine iron near lava").contains("water bucket"));
    }
}
