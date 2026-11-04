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
        assertEquals("-", parsed("Steve, go see what's over that hill"));
        // "Get me a diamond pickaxe" is an order code plans now, as "make me" is (MA7.1).
        assertEquals("PRODUCE:diamond pickaxe", parsed("Steve, get me a diamond pickaxe"));
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
        assertEquals("MINE_ORE:iron", parsed("go mine some iron"), "an order to mine (MA7.8), not a place called the mine");
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

    @Test void theWordsAGoalKeepsAnOrderInAndTheQuestions() {
        var fill = CommandParser.parse("fill the chest at 12, -59, -8 with stone bricks", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.PRODUCE, fill.kind());
        assertEquals("chestat|12,-59,-8|stone bricks", fill.value());
        assertEquals(0, fill.count().intValue());
        var put = CommandParser.parse("put 4 cooked beef in the chest at 1, 2, 3", "Pal").orElseThrow();
        assertEquals("chestat|1,2,3|cooked beef", put.value());
        assertEquals(4, put.count().intValue());
        assertEquals(CommandParser.Kind.WHATS_NEXT, CommandParser.parse("what's next?", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.WHY, CommandParser.parse("why", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.NEEDS, CommandParser.parse("what do you need", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.PRODUCE, CommandParser.parse("I need 3 furnaces", "Pal").orElseThrow().kind(), "still an order");
        var give = CommandParser.parse("give me 8 food", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.GIVE, give.kind());
        assertEquals("food", give.value());
        assertEquals(8, give.count().intValue());
        assertEquals(CommandParser.Kind.FOOD_BOTH, CommandParser.parse("get food for both of us", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.BUILD, CommandParser.parse("build us a shelter here", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.BUILD, CommandParser.parse("build a hut", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.LIGHT, CommandParser.parse("light up the area", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.LIGHT, CommandParser.parse("put some torches around", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.PRIME, CommandParser.parse("prime the world", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.PRIME, CommandParser.parse("set us up", "Pal").orElseThrow().kind());
        assertEquals(-1, CommandParser.parse("hand me a stack of torches", "Pal").orElseThrow().count().intValue());
        // A player by name (MA10.7); whether they are a player, and one it may fight, the loop decides.
        var hunt = CommandParser.parse("kill Rival_99", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.HUNT_PLAYER, hunt.kind());
        assertEquals("rival_99", hunt.value());
        assertEquals(CommandParser.Kind.HUNT_PLAYER, CommandParser.parse("Pal, hunt down Steve", "Pal").orElseThrow().kind());
        assertEquals("steve", CommandParser.parse("go after steve please", "Pal").orElseThrow().value());
        assertFalse(CommandParser.parse("kill 5 cows", "Pal").map(x -> x.kind() == CommandParser.Kind.HUNT_PLAYER).orElse(false));
        assertNotEquals(CommandParser.Kind.HUNT_PLAYER, CommandParser.parse("hunt for a village", "Pal").map(x -> x.kind()).orElse(null));
        // Standing orders about places (MA10.2).
        var keep = CommandParser.parse("don't touch my garden", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.PROTECT, keep.kind());
        assertEquals("garden", keep.value());
        assertEquals("wheat farm", CommandParser.parse("leave the wheat farm alone please", "Pal").orElseThrow().value());
        var free = CommandParser.parse("you can touch the garden now", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.UNPROTECT, free.kind());
        assertEquals("garden", free.value());
        assertEquals(CommandParser.Kind.UNPROTECT, CommandParser.parse("stop protecting my farm", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.GUARD_OFF, CommandParser.parse("stop protecting steve", "Pal").orElseThrow().kind(), "a player: guard duty");
        assertEquals(CommandParser.Kind.PROGRESS, CommandParser.parse("progress report", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.PORTAL, CommandParser.parse("build a nether portal", "Pal").orElseThrow().kind());
        assertEquals(CommandParser.Kind.PORTAL, CommandParser.parse("make us a portal here", "Pal").orElseThrow().kind());
        var ench = CommandParser.parse("enchant your pickaxe", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.ENCHANT, ench.kind());
        assertEquals("pickaxe", ench.value());
        assertEquals("gear", CommandParser.parse("enchant your gear", "Pal").orElseThrow().value());
        var trade = CommandParser.parse("trade for emeralds", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.TRADE, trade.kind());
        assertEquals("emerald", trade.value());
        assertEquals(CommandParser.Kind.TRADE, CommandParser.parse("sell your wheat for emeralds", "Pal").orElseThrow().kind());
        assertEquals("bread", CommandParser.parse("buy bread from the villager", "Pal").orElseThrow().value());
        var brew = CommandParser.parse("brew a potion of fire resistance", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.BREW, brew.kind());
        assertEquals("fire_resistance", brew.value());
        assertEquals("swiftness", CommandParser.parse("brew speed potions", "Pal").orElseThrow().value());
        var breed = CommandParser.parse("breed the cows", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.BREED, breed.kind());
        assertEquals("cows", breed.value());
        var get = CommandParser.parse("get me 3 oak logs", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.PRODUCE, get.kind());
        assertEquals("oak logs", get.value());
        assertEquals(3, get.count().intValue());
        assertEquals(CommandParser.Kind.PRODUCE, CommandParser.parse("gather 64 cobblestone", "Pal").orElseThrow().kind());
        assertEquals(4, CommandParser.parse("go fishing", "Pal").orElseThrow().count().intValue());
        var fish = CommandParser.parse("catch me 2 fish", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.FISH, fish.kind());
        assertEquals(2, fish.count().intValue());
        assertEquals("add", CommandParser.parse("add a patrol point here", "Pal").orElseThrow().value());
        assertEquals("clear", CommandParser.parse("clear the patrol", "Pal").orElseThrow().value());
        assertEquals(CommandParser.Kind.GUARD_HERE, CommandParser.parse("guard that", "Pal").orElseThrow().kind());
        assertEquals("that", CommandParser.parse("guard that horse", "Pal").orElseThrow().value());
    }

    @Test void ordersToMineOnItsOwn() {
        var d = CommandParser.parse("mine diamonds", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.MINE_ORE, d.kind());
        assertEquals("diamonds", d.value());
        assertEquals(0, d.count().intValue(), "until told to stop, or its pack fills");
        var three = CommandParser.parse("go get me 3 diamonds", "Pal").orElseThrow();
        assertEquals("diamonds", three.value());
        assertEquals(3, three.count().intValue());
        assertEquals("iron|here", CommandParser.parse("mine 5 iron here", "Pal").orElseThrow().value());
        assertEquals(5, CommandParser.parse("mine 5 iron here", "Pal").orElseThrow().count().intValue());
        assertEquals("lapis lazuli", CommandParser.parse("dig for lapis lazuli", "Pal").orElseThrow().value());
        assertEquals("ore", CommandParser.parse("go mining", "Pal").orElseThrow().value());
        assertEquals("ore", CommandParser.parse("mine until I say stop", "Pal").orElseThrow().value());
        assertEquals("iron", CommandParser.parse("mine some iron until I say stop", "Pal").orElseThrow().value());
        assertTrue(CommandParser.parse("mine 10 stone", "Pal").map(c -> c.kind() != CommandParser.Kind.MINE_ORE).orElse(true),
                "stone is not an ore order");
    }

    @Test void ordersToMakeThings() {
        var fill = CommandParser.parse("Fill this chest with stone bricks", "Pal").orElseThrow();
        assertEquals(CommandParser.Kind.PRODUCE, fill.kind());
        assertEquals("chest|stone bricks", fill.value());
        assertEquals(0, fill.count().intValue(), "as many as fill it");
        assertEquals("anychest|stone bricks", CommandParser.parse("I want an entire chest of stone bricks", "Pal").orElseThrow().value(),
                "a chest's worth: the one by me, or one it puts down");
        assertEquals("anychest|food", CommandParser.parse("fill up a chest with food", "Pal").orElseThrow().value());
        assertEquals("chest|food", CommandParser.parse("fill my chest with food", "Pal").orElseThrow().value());
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
