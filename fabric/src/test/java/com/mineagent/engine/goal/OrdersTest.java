package com.mineagent.engine.goal;

import com.mineagent.engine.planning.GoalBook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Orders in a row and orders that stand (SMH plan 70, MA6.3). */
final class OrdersTest {
    @Test void oneLongTaskThenAnotherUntilFurtherNotice() {
        var o = Orders.split("fill this chest with stone bricks, then mine diamonds until further notice");
        assertEquals(2, o.size());
        assertEquals("fill this chest with stone bricks", o.get(0).text());
        assertFalse(o.get(0).standing());
        assertEquals("mine diamonds", o.get(1).text());
        assertTrue(o.get(1).standing(), "until further notice: it stands");
        assertEquals("mine diamonds" + Orders.STANDS, o.get(1).goalText());
        assertTrue(Orders.stands(o.get(1).goalText()));
        assertEquals("mine diamonds", Orders.withoutMark(o.get(1).goalText()));
    }

    @Test void theWaysPeopleSayThen() {
        assertEquals(3, Orders.split("first make me 64 torches, then mine iron and then go home").size());
        assertEquals("make me 64 torches", Orders.split("first make me 64 torches, then mine iron").get(0).text());
        assertEquals(2, Orders.split("make a bed. after that come back here").size());
        assertEquals(2, Orders.split("mine 10 iron; once that's done, smelt it").size());
        assertTrue(Orders.split("make me 64 torches").isEmpty(), "one order is not a row");
        assertTrue(Orders.split("strengthen the wall").isEmpty(), "\"then\" inside a word is not a then");
        assertTrue(Orders.order("keep me safe until I say stop").standing());
        assertEquals("keep me safe", Orders.order("keep me safe until I say stop").text());
    }

    @Test void theGoalBookRunsThemInTheirOrderThenWhatTheyInterrupted() {
        var book = new GoalBook();
        var old = book.add("build a house", "owner", 1, s -> false);
        var made = book.chain(List.of("fill the chest at 1, 2, 3 with stone bricks", "mine diamonds"), "owner", 5);
        assertEquals(2, made.size());
        assertEquals(made.get(0).id(), book.active().orElseThrow().id(), "the first order now");
        assertEquals(List.of(made.get(1).id(), old.id()), book.upcoming().stream().map(GoalBook.Goal::id).toList(),
                "the second next, then the house");
        assertEquals(made.get(1).id(), book.complete().orElseThrow().id());
        assertEquals(old.id(), book.complete().orElseThrow().id(), "then back to the house");
        // Saved and loaded, the order holds.
        var book2 = new GoalBook();
        var a = new GoalBook();
        a.add("one", "owner", 1, s -> false);
        a.chain(List.of("two", "three"), "owner", 2);
        book2.importState(a.exportState());
        assertEquals("two", book2.active().orElseThrow().text());
        assertEquals(List.of("three", "one"), book2.upcoming().stream().map(GoalBook.Goal::text).toList());
    }
}
