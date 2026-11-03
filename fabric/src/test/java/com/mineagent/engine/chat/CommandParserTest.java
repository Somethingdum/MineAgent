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
}
