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
