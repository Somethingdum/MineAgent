package com.mineagent.engine.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA1.7: talks to anyone, obeys only the owner and the trusted. */
final class ChatRoutingTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID friend = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final List<String> trusted = List.of(friend.toString());

    @Test void theOwnerIsTheOwnerAnywhere() {
        assertEquals(ChatRouting.Route.OWNER, ChatRouting.route(owner, owner, trusted, 5000, false));
    }

    @Test void aTrustedPlayerGivesOrdersWithin32Blocks() {
        assertEquals(ChatRouting.Route.TRUSTED, ChatRouting.route(friend, owner, trusted, 0, false));
        assertEquals(ChatRouting.Route.TRUSTED, ChatRouting.route(friend, owner, trusted, 32, false));
        // Farther: only talked with, and only when they name the companion.
        assertEquals(ChatRouting.Route.TALK, ChatRouting.route(friend, owner, trusted, 32.5, true));
        assertEquals(ChatRouting.Route.IGNORE, ChatRouting.route(friend, owner, trusted, 32.5, false));
    }

    @Test void anyoneElseIsTalkedWithWhenNamingItOrWithin8Blocks() {
        assertEquals(ChatRouting.Route.TALK, ChatRouting.route(stranger, owner, trusted, 8, false));
        assertEquals(ChatRouting.Route.TALK, ChatRouting.route(stranger, owner, trusted, 200, true));
        assertEquals(ChatRouting.Route.TALK, ChatRouting.route(stranger, owner, trusted, Double.POSITIVE_INFINITY, true));
        assertEquals(ChatRouting.Route.IGNORE, ChatRouting.route(stranger, owner, trusted, 8.01, false));
        // A stranger standing right there never becomes trusted.
        assertNotEquals(ChatRouting.Route.TRUSTED, ChatRouting.route(stranger, owner, trusted, 0, true));
        assertEquals(ChatRouting.Route.IGNORE, ChatRouting.route(null, owner, trusted, 0, true));
        assertEquals(ChatRouting.Route.TALK, ChatRouting.route(stranger, owner, null, 1, false));
    }

    @Test void namingTheCompanion() {
        assertTrue(ChatRouting.mentions("hey Steve, come here", "steve"));
        assertTrue(ChatRouting.mentions("@Steve hi", "Steve"));
        assertFalse(ChatRouting.mentions("Stevens is here", "Steve"));
        assertTrue(ChatRouting.mentions("小明你好", "小明"));
        assertFalse(ChatRouting.mentions("hello", ""));
        assertFalse(ChatRouting.mentions(null, "Steve"));
    }
}
