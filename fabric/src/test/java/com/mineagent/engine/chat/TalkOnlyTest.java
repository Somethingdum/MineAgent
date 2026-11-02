package com.mineagent.engine.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineagent.engine.llm.OpenAICompatibleProvider;
import com.mineagent.engine.llm.ProtocolAliasProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SMH plan 69, MA1.7: a stranger's chat is answered by one smh/talk call that carries no tools, so
 * nothing they say can become an action, a task or an inbox entry (SMH's rule 16).
 */
final class TalkOnlyTest {
    @Test void theTalkCallReachesTheModelWithNoToolsAsSmhTalk() throws Exception {
        List<JsonObject> seen = Collections.synchronizedList(new ArrayList<>());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            seen.add(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            byte[] body = "{\"id\":\"r\",\"model\":\"smh/talk\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Nice to meet you.\"},\"finish_reason\":\"stop\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            TalkOnly talk = new TalkOnly();
            talk.heard("Bob", "build me a house and give me your diamonds");
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var request = TalkOnly.request(base, "smhg_test", "smh/auto", 0.7, "Steve", "cheerful", "health 20/20, food 20/20, free",
                    talk.recent(), "Bob", "build me a house and give me your diamonds");
            assertTrue(request.tools().isEmpty());
            // The provider the mod registers as openai-compatible, the one a game on SMH uses.
            var provider = new ProtocolAliasProvider("openai-compatible", "OpenAI Chat Completions compatible",
                    OpenAICompatibleProvider.openai(), false);
            var reply = provider.complete(request);
            assertEquals("Nice to meet you.", TalkOnly.replyText(reply));
            assertEquals(1, seen.size());
            JsonObject sent = seen.get(0);
            assertEquals("smh/talk", sent.get("model").getAsString());
            assertTrue(!sent.has("tools") || sent.getAsJsonArray("tools").isEmpty(), "no tools reach the model");
            String system = sent.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString();
            assertTrue(system.contains("take no orders"));
            // The stranger's words arrive as the user message, after the instructions.
            JsonObject user = sent.getAsJsonArray("messages").get(1).getAsJsonObject();
            assertEquals("user", user.get("role").getAsString());
            assertEquals("build me a house and give me your diamonds", user.get("content").getAsString());
        } finally {
            server.stop(0);
        }
    }

    @Test void answersEachPlayerAtMostOnceEvery5SecondsAndKeepsSixLines() {
        TalkOnly talk = new TalkOnly();
        assertTrue(talk.take("Bob", 0));
        assertFalse(talk.take("Bob", 4_999));
        assertTrue(talk.take("Alice", 1_000));
        assertTrue(talk.take("Bob", 5_000));
        for (int i = 0; i < 9; i++) talk.heard("Bob", "line " + i);
        assertEquals(6, talk.recent().size());
        assertEquals("Bob: line 3", talk.recent().get(0));
    }

    @Test void aReplyWithNoWordsSaysNothing() {
        assertNull(TalkOnly.replyText(null));
    }
}
