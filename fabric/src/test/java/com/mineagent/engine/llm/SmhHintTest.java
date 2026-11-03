package com.mineagent.engine.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69: when SMH fails, the owner is told why in plain words. */
final class SmhHintTest {
    private static final String HERE = "http://127.0.0.1:47821/apps";

    @Test void smhNotRunningOnThisPc() {
        assertEquals("I can't reach SMH on this PC. Start SMH and I'll carry on.",
                SmhHint.forFailure(HERE, null, "openai API transport failure: ConnectException", new java.net.ConnectException()));
        assertTrue(SmhHint.forFailure("http://100.64.1.2:47821/apps", null, "x", new java.net.ConnectException()).contains("100.64.1.2"));
    }

    @Test void smhsOwnAnswers() {
        assertTrue(SmhHint.forFailure(HERE, 503, "{\"error\":{\"type\":\"no_model\"}}", null).contains("no model"));
        assertNull(SmhHint.forFailure(HERE, 503, "{\"error\":{\"type\":\"busy\"}}", null), "busy is retried quietly");
        assertTrue(SmhHint.forFailure(HERE, 401, "bad_key", null).contains("no key is needed"));
        assertTrue(SmhHint.forFailure("http://100.64.1.2:47821/apps", 401, "bad_key", null).contains("Minecraft card"));
        assertTrue(SmhHint.forFailure(HERE, 404, "not_found", null).contains("switched off"));
    }

    @Test void anythingButSmhKeepsItsOwnMessage() {
        assertNull(SmhHint.forFailure("https://api.example.com/v1", null, "ConnectException", new java.net.ConnectException()));
        assertNull(SmhHint.forFailure(HERE, 500, "oops", null));
        assertFalse(SmhHint.smh(null));
        assertTrue(SmhHint.smh("http://127.0.0.1:47821/apps/"));
        assertTrue(SmhHint.local("http://localhost:47821/apps"));
    }
}
