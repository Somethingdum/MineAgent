package com.mineagent.engine.planning;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA3.1 and MA3.2: goals that outlast a session, one active, done only when the count agrees. */
final class GoalBookTest {
    private static final Predicate<String> ITEMS = Set.of("oak_log", "iron_ingot", "torch", "diamond", "raw_iron",
            "iron_ore", "chest", "cobblestone", "bread", "stone", "spruce_planks")::contains;

    @Test void oneGoalIsActiveAndTheRestQueue() {
        GoalBook book = new GoalBook();
        var a = book.add("get 64 oak logs", "owner", 10, ITEMS);
        var b = book.add("build a shelter", "owner", 20, ITEMS);
        assertEquals(GoalBook.Status.ACTIVE, a.status());
        assertEquals(GoalBook.Status.PAUSED, b.status());
        assertEquals(a.id(), book.active().orElseThrow().id());
        assertEquals(1, book.list().stream().filter(g -> g.status() == GoalBook.Status.ACTIVE).count());
    }

    @Test void switchingKeepsEachGoalsProgress() {
        GoalBook book = new GoalBook();
        var a = book.add("get 64 oak logs", "owner", 10, ITEMS);
        var b = book.add("build a shelter", "owner", 20, ITEMS);
        book.milestone("chopped the first tree", 30);
        book.stepping("chop the next tree", "16 logs so far", false);
        assertTrue(book.switchTo(b.id()));
        assertEquals(b.id(), book.active().orElseThrow().id());
        assertTrue(book.switchTo(a.id()));
        var back = book.active().orElseThrow();
        assertEquals(1, back.milestones().size());
        assertEquals("16 logs so far", back.progress());
        assertEquals(1, back.steps());
        assertEquals(GoalBook.Status.PAUSED, book.get(b.id()).orElseThrow().status());
    }

    @Test void goalsSurviveASaveAndAReload() {
        GoalBook book = new GoalBook();
        book.add("get 64 oak logs", "owner", 10, ITEMS);
        book.add("build a shelter", "Alex", 20, ITEMS);
        book.milestone("first tree", 30);
        book.setStock("torch", 64);
        Gson gson = new Gson();
        String saved = gson.toJson(book.exportState());
        GoalBook loaded = new GoalBook();
        loaded.importState(gson.fromJson(saved, GoalBook.State.class));
        assertEquals(book.list(), loaded.list());
        assertEquals(book.stock(), loaded.stock());
        // New ids carry on after the loaded ones.
        assertEquals("g3", loaded.add("dig a cellar", "owner", 40, ITEMS).id());
    }

    @Test void aFileWithTwoActiveGoalsLoadsWithOne() {
        var g1 = new GoalBook.Goal("g1", "a", "owner", 1, GoalBook.Status.ACTIVE, List.of(), "", List.of(), 0, "", 0, 0);
        var g2 = new GoalBook.Goal("g2", "b", "owner", 2, GoalBook.Status.ACTIVE, List.of(), "", List.of(), 0, "", 0, 0);
        GoalBook book = new GoalBook();
        book.importState(new GoalBook.State(List.of(g1, g2), List.of(), 3));
        assertEquals(1, book.list().stream().filter(g -> g.status() == GoalBook.Status.ACTIVE).count());
    }

    @Test void amountsAreReadOnlyWhereTheGoalMeansToHoldThem() {
        assertEquals(List.of(new GoalBook.Need("#logs", 64), new GoalBook.Need("iron_ingot", 20)),
                GoalBook.needsIn("Get 64 logs and 20 iron ingots, then build a house", ITEMS));
        assertEquals(List.of(new GoalBook.Need("raw_iron", 30)), GoalBook.needsIn("mine 30 iron ore", ITEMS));
        assertEquals(List.of(new GoalBook.Need("raw_iron", 10)), GoalBook.needsIn("get 10 iron ore", ITEMS));
        assertEquals(List.of(new GoalBook.Need("cobblestone", 64)), GoalBook.needsIn("mine 64 stone", ITEMS));
        assertEquals(List.of(new GoalBook.Need("torch", 16)), GoalBook.needsIn("make 16 torches", ITEMS));
        assertEquals(List.of(new GoalBook.Need("spruce_planks", 32)), GoalBook.needsIn("craft 32 spruce planks", ITEMS));
        assertEquals(List.of(), GoalBook.needsIn("smelt 20 iron ore into ingots", ITEMS), "used up, not held");
        assertEquals(List.of(), GoalBook.needsIn("build a 5 by 5 house", ITEMS));
        assertEquals(List.of(), GoalBook.needsIn("get 3 unobtainium", ITEMS), "an item it does not know");
    }

    @Test void doneOnlyWhenTheCountAgrees() {
        GoalBook book = new GoalBook();
        book.add("get 64 oak logs", "owner", 10, ITEMS);
        // "Oak logs" means oak; "logs" alone would be any kind.
        var shortBy = book.unmet(item -> item.equals("oak_log") ? 40 : 0);
        assertEquals(List.of(new GoalBook.Need("oak_log", 24)), shortBy);
        var step = GoalBook.parseStep("<think>x</think>{\"done\":true,\"next\":\"\"}", shortBy).orElseThrow();
        assertFalse(step.done());
        assertTrue(step.claimedShort());
        assertEquals("Still short: 24 oak log", step.next());
        var really = GoalBook.parseStep("{\"done\":true}", book.unmet(item -> 64)).orElseThrow();
        assertTrue(really.done());
        assertTrue(GoalBook.parseStep("no json here", List.of()).isEmpty());
        assertTrue(GoalBook.parseStep("{\"done\":false}", List.of()).isEmpty(), "not done needs a next step");
    }

    @Test void theLoopRunsAThreeMilestoneGoalToDone() {
        GoalBook book = new GoalBook();
        book.add("get 3 diamonds", "owner", 0, ITEMS);
        book.add("build a shelter", "owner", 1, ITEMS);
        String[] replies = {"{\"done\":false,\"next\":\"dig down to y=-58\"}",
                "{\"done\":false,\"next\":\"strip mine for diamonds\"}",
                "{\"done\":true}"};
        int[] diamonds = {0, 1, 3};
        for (int i = 0; i < replies.length; i++) {
            int have = diamonds[i];
            book.milestone("step " + i + " verified", 100L * i);
            var step = GoalBook.parseStep(replies[i], book.unmet(item -> item.equals("diamond") ? have : 0)).orElseThrow();
            if (step.done()) {
                var next = book.complete();
                assertEquals("build a shelter", next.orElseThrow().text(), "the queue moves on");
            } else {
                assertTrue(book.stepping(step.next(), null, step.claimedShort()).isPresent());
            }
        }
        assertEquals(GoalBook.Status.DONE, book.get("g1").orElseThrow().status());
        assertEquals(3, book.get("g1").orElseThrow().milestones().size());
    }

    @Test void aGoalThatIsNotGettingThereIsPausedNotLoopedForever() {
        GoalBook book = new GoalBook();
        book.add("get 64 oak logs", "owner", 0, ITEMS);
        for (int i = 0; i < GoalBook.MAX_SHORT_DONE - 1; i++) assertTrue(book.stepping("Still short", null, true).isPresent());
        assertTrue(book.stepping("Still short", null, true).isEmpty());
        assertEquals(GoalBook.Status.PAUSED, book.get("g1").orElseThrow().status());
        assertTrue(book.active().isEmpty());
        // The owner can close it.
        book.switchTo("g1");
        book.markDone("g1");
        assertEquals(GoalBook.Status.DONE, book.get("g1").orElseThrow().status());
    }

    @Test void theSameStepComingBackIsCounted() {
        GoalBook book = new GoalBook();
        book.add("find a village", "owner", 0, ITEMS);
        assertEquals(0, book.stepping("Explore north", null, false).orElseThrow().repeats());
        assertEquals(1, book.stepping("explore north.", null, false).orElseThrow().repeats());
        assertEquals(0, book.stepping("explore east", null, false).orElseThrow().repeats());
    }

    @Test void droppingTheActiveGoalStartsTheNextAndFullBooksRefuse() {
        GoalBook book = new GoalBook();
        book.add("a", "owner", 0, ITEMS);
        book.add("b", "owner", 1, ITEMS);
        assertEquals("b", book.drop("g1").orElseThrow().text());
        for (int i = 0; i < GoalBook.MAX_OPEN - 1; i++) book.add("more " + i, "owner", 2 + i, ITEMS);
        assertThrows(IllegalStateException.class, () -> book.add("one too many", "owner", 99, ITEMS));
    }

    @Test void standingOrdersComeDueWhenShort() {
        GoalBook book = new GoalBook();
        assertTrue(book.setStock("minecraft:torch", 64));
        book.setStock("bread", 16);
        var due = book.dueStock(item -> item.equals("torch") ? 64 : 3).orElseThrow();
        assertEquals("bread", due.getKey().item());
        assertEquals(3, (int) due.getValue());
        assertTrue(book.dueStock(item -> 100).isEmpty());
        book.setStock("bread", 0);
        assertEquals(List.of(new GoalBook.Stock("torch", 64)), book.stock());
    }

    @Test void aLineSaysWhereAGoalStands() {
        var g = new GoalBook.Goal("g2", "get 64 oak logs", "owner", 0, GoalBook.Status.ACTIVE, List.of(), "16 so far",
                List.of(new GoalBook.Need("#logs", 64)), 3, "", 0, 0);
        assertEquals("g2 (active) get 64 oak logs: 3 steps, 16 so far, short 48 #logs",
                GoalBook.line(g, List.of(new GoalBook.Need("#logs", 48))));
        assertNotNull(Map.of());
    }
}
