package com.mineagent.engine.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, after the Andy Omega plan's Lexer: the owner's plain commands, understood without a model. */
final class CommandParserTest {
    private static String parsed(String line) {
        return CommandParser.parse(line, "Steve").map(c -> c.kind() + (c.value() != null ? ":" + c.value() : "")
                + (c.kind() == CommandParser.Kind.DROP ? ":" + c.count() : "")).orElse("-");
    }

    @Test void plainCommandsAreUnderstoodInTheWaysPeopleSayThem() {
        assertEquals("FOLLOW", parsed("[Bob]: Steve, follow me!"));
        assertEquals("STOP", parsed("stop"));
        assertEquals("COME", parsed("please come here"));
        assertEquals("STAY", parsed("stay here"));
        assertEquals("STATUS", parsed("what are you doing?"));
        assertEquals("CANCEL", parsed("never mind"));
        assertEquals("EAT", parsed("eat"));
        assertEquals("MODE:auto", parsed("auto"));
        assertEquals("MODE:auto", parsed("on your own"));
        assertEquals("MODE:servant", parsed("go servant mode"));
        assertEquals("MODE:parked", parsed("park"));
        assertEquals("MODE:escort", parsed("protect me"));
        assertEquals("DROP:cobblestone:32", parsed("drop 32 cobble"));
        assertEquals("DROP:torch:64", parsed("drop a stack of torches"));
        assertEquals("DROP:diamond:2", parsed("toss two diamonds"));
    }

    @Test void guardingASpotOrAPlayerAndStandingDown() {
        assertEquals("GUARD_HERE:null", parsedGuard("guard here"));
        assertEquals("GUARD_HERE:8", parsedGuard("Steve, guard this chest radius 8"));
        assertEquals("GUARD_HERE:20", parsedGuard("protect the base within 20 blocks"));
        assertEquals("GUARD_PLAYER:alex_99", parsedGuard("guard alex_99"));
        assertEquals("MODE:escort", parsedGuard("guard me"));
        assertEquals("MODE:escort", parsedGuard("defend me"));
        assertEquals("GUARD_OFF", parsedGuard("stand down"));
        assertEquals("GUARD_OFF", parsedGuard("stop guarding"));
        // A warning is not an order to guard; an unknown place goes to the model.
        assertEquals("-", parsedGuard("watch it"));
        assertEquals("-", parsedGuard("guard the castle by the river"));
    }

    private static String parsedGuard(String line) {
        return CommandParser.parse(line, "Steve").map(c -> c.kind() + (c.kind() == CommandParser.Kind.GUARD_HERE
                ? ":" + c.count() : c.value() != null ? ":" + c.value() : "")).orElse("-");
    }

    @Test void longGoalsGoInTheGoalBookAndCarryOnResumes() {
        // Number words become digits, which the goal book counts.
        assertEquals("GOAL_ADD:build a stone castle by the river with 4 towers and a moat around it",
                parsed("Steve, goal: build a stone castle by the river with four towers and a moat around it"));
        assertEquals("GOAL_ADD:get 64 oak logs", parsed("new goal - get 64 oak logs"));
        assertEquals("RESUME", parsed("carry on"));
        assertEquals("RESUME", parsed("back to work, Steve"));
        assertEquals("-", parsed("what is your goal"));
    }

    @Test void aNegationIsAStopNeverTheVerbAfterIt() {
        assertEquals("STOP", parsed("don't go there"));
        assertEquals("STOP", parsed("wait no, follow me"));
    }

    @Test void anythingElseGoesToTheModel() {
        assertEquals("-", parsed("Steve, get me a diamond pickaxe"));
        assertEquals("-", parsed("build a house near the lake please"));
        assertEquals("-", parsed("drop all your stuff"));
        assertEquals("-", parsed("can you tell me what the weather is like in the nether today and why"));
    }

    @Test void goingAfkAndComingBackAreTheOwnersWords() {
        assertEquals("AFK", parsed("afk"));
        assertEquals("AFK", parsed("brb"));
        assertEquals("AFK", parsed("be right back!"));
        assertEquals("AFK", parsed("Steve, going afk"));
        assertEquals("AFK", parsed("back in a bit"));
        assertEquals("BACK", parsed("back"));
        assertEquals("BACK", parsed("I'm back"));
        assertEquals("COME", parsed("here"));
        assertEquals("-", parsed("go back to the base and get the iron"));
    }

    @Test void placesAndTheWayBackAreUnderstood() {
        assertEquals("HOME_HERE", parsed("this is home"));
        assertEquals("HOME_HERE", parsed("Steve, set home here"));
        assertEquals("HOME_HERE", parsed("remember this as home"));
        assertEquals("HOME_HERE", parsed("this is our base"));
        assertEquals("HOME_HERE", parsed("remember this place as our base"));
        assertEquals("REMEMBER_PLACE:the mine", parsed("remember this as the mine"));
        assertEquals("REMEMBER_PLACE:iron spot", parsed("call this place iron spot"));
        assertEquals("GO_PLACE:home", parsed("go home"));
        assertEquals("GO_PLACE:home", parsed("let's go back home!"));
        assertEquals("GO_PLACE:home", parsed("take me to base"));
        assertEquals("GO_PLACE:the mine", parsed("go to the mine"));
        assertEquals("GO_PLACE:the village", parsed("please take me to the village"));
        assertEquals("WHERE_PLACE:the village", parsed("where's the village?"));
        assertEquals("WHERE_PLACE:home", parsed("how far is home"));
        assertEquals("WAY_BACK:out", parsed("get us out of here"));
        assertEquals("WAY_BACK:out", parsed("lead the way out"));
        assertEquals("WAY_BACK:back", parsed("go back the way we came"));
        assertEquals("WAY_BACK:back", parsed("retrace our steps"));
        // Not these: a resume, a coming back, and an order that only names a place.
        assertEquals("RESUME", parsed("get back to work"));
        assertEquals("BACK", parsed("back"));
        assertEquals("RESUME", parsed("go on"));
        assertEquals("-", parsed("go mine some iron"));
    }

    @Test void questionsForItsMemoryAreUnderstood() {
        assertEquals("WHERE_SEEN:diamonds", parsed("where did you see diamonds?"));
        assertEquals("WHERE_SEEN:iron", parsed("Steve, have you seen any iron"));
        assertEquals("WHERE_SEEN:cows", parsed("where can I find cows"));
        assertEquals("WHERE_PUT:the iron", parsed("where did you put the iron?"));
        assertEquals("WHERE_PUT:my diamonds", parsed("where'd you put my diamonds"));
        assertEquals("WHAT_HAPPENED:today", parsed("what did we do today?"));
        assertEquals("WHAT_HAPPENED:today", parsed("what happened"));
        assertEquals("WHAT_HAPPENED:yesterday", parsed("what did you do yesterday"));
        assertEquals("WHAT_HAPPENED:away", parsed("what happened while I was away?"));
        assertEquals("WHAT_HAPPENED:away", parsed("what did I miss"));
        // Not these: what it is doing now, and a place.
        assertEquals("STATUS", parsed("what are you doing?"));
        assertEquals("WHERE_PLACE:the village", parsed("where's the village"));
    }

    @Test void findingAndExploringAreUnderstood() {
        assertEquals("FIND_PLACE:village", parsed("find a village"));
        assertEquals("FIND_PLACE:ruined portal", parsed("Steve, go look for a ruined portal"));
        assertEquals("FIND_PLACE:diamonds", parsed("find me diamonds"));
        assertEquals("FIND_PLACE:", parsed("explore"));
    }

    @Test void goingAfkWithWhatToDoMeanwhile() {
        var c = CommandParser.parse("Going AFK, fill up a chest with food while I'm out.", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.AFK, c.kind());
        assertEquals("fill up a chest with food while i'm out", c.value(), "any length; the order goes with it");
        assertEquals("make sure i dont die", CommandParser.parse("Going AFK, make sure I dont die", "Pal").orElseThrow().value());
        assertEquals("block yourself in, don't die",
                CommandParser.parse("Going AFK, block yourself in, don't die", "Pal").orElseThrow().value());
        assertEquals("keep me safe", CommandParser.parse("brb - keep me safe", "Pal").orElseThrow().value());
        assertEquals("smelt the iron", CommandParser.parse("going afk and smelt the iron", "Pal").orElseThrow().value());
        var plain = CommandParser.parse("going afk", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.AFK, plain.kind());
        assertNull(plain.value());
        assertTrue(CommandParser.parse("afk farm is broken", "Pal").isEmpty(), "talk about an AFK farm is not going AFK");
    }

    @Test void ordersToMakeThings() {
        var fill = CommandParser.parse("Fill this chest with stone bricks", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.PRODUCE, fill.kind());
        assertEquals("chest|stone bricks", fill.value());
        assertEquals(0, fill.count().intValue(), "as many as fill it");
        assertEquals("chest|stone bricks", CommandParser.parse("I want an entire chest of stone bricks", "Pal").orElseThrow().value());
        assertEquals("chest|cobblestone", CommandParser.parse("fill it with cobblestone", "Pal").orElseThrow().value());
        var put = CommandParser.parse("put 2 stacks of torches in this chest", "Pal").orElseThrow();
        assertEquals("chest|torches", put.value());
        assertEquals(-2, put.count().intValue(), "two stacks");
        var make = CommandParser.parse("make me 64 torches", "Pal").orElseThrow();
        assertEquals("torches", make.value());
        assertEquals(64, make.count().intValue());
        assertEquals(1, CommandParser.parse("craft an iron pickaxe", "Pal").orElseThrow().count().intValue());
        assertEquals(3, CommandParser.parse("I need 3 furnaces", "Pal").orElseThrow().count().intValue());
        assertEquals(-1, CommandParser.parse("make a stack of glass", "Pal").orElseThrow().count().intValue());
        assertTrue(CommandParser.parse("make it rain", "Pal").map(c -> c.kind() != CommandParser.Kind.PRODUCE).orElse(true),
                "no count: not an order to make something");
    }
}
