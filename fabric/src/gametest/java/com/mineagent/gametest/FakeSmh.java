package com.mineagent.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * A stand-in for SMH on this PC, where the mod looks for it by default (http://127.0.0.1:47821/apps): it answers
 * /apps/v1/chat/completions with one short line and keeps what it was sent, so a scene can see what the companion
 * asked. Started once; it lives as long as the test server.
 */
final class FakeSmh {
    record Call(String path, String authorization, JsonObject body) {
        /** Every message's text, run together. */
        String text() {
            StringBuilder b = new StringBuilder();
            if (body.has("messages") && body.get("messages").isJsonArray()) {
                for (JsonElement m : body.getAsJsonArray("messages")) {
                    if (m.isJsonObject() && m.getAsJsonObject().has("content") && m.getAsJsonObject().get("content").isJsonPrimitive()) {
                        b.append(m.getAsJsonObject().get("content").getAsString()).append('\n');
                    }
                }
            }
            return b.toString();
        }

        boolean hasTools() {
            return body.has("tools") && body.get("tools").isJsonArray() && !body.getAsJsonArray("tools").isEmpty();
        }

        String model() {
            return body.has("model") && body.get("model").isJsonPrimitive() ? body.get("model").getAsString() : "";
        }
    }

    static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static HttpServer server;
    private static String failure;

    private FakeSmh() {}

    /** Start it on 127.0.0.1:47821, once; why it could not, or null. */
    static synchronized String start() {
        if (server != null) return null;
        if (failure != null) return failure;
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 47821), 0);
            s.createContext("/apps/", exchange -> {
                try {
                    String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject body;
                    try {
                        JsonElement e = JsonParser.parseString(raw.isBlank() ? "{}" : raw);
                        body = e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
                    } catch (RuntimeException bad) {
                        body = new JsonObject();
                    }
                    CALLS.add(new Call(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"), body));
                    JsonObject message = new JsonObject();
                    message.addProperty("role", "assistant");
                    message.addProperty("content", "On it.");
                    JsonObject choice = new JsonObject();
                    choice.addProperty("index", 0);
                    choice.add("message", message);
                    choice.addProperty("finish_reason", "stop");
                    JsonArray choices = new JsonArray();
                    choices.add(choice);
                    JsonObject usage = new JsonObject();
                    usage.addProperty("prompt_tokens", 1);
                    usage.addProperty("completion_tokens", 1);
                    usage.addProperty("total_tokens", 2);
                    JsonObject reply = new JsonObject();
                    reply.addProperty("id", "fake");
                    reply.addProperty("model", body.has("model") ? body.get("model").getAsString() : "smh/act");
                    reply.add("choices", choices);
                    reply.add("usage", usage);
                    byte[] out = reply.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, out.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(out);
                    }
                } finally {
                    exchange.close();
                }
            });
            s.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "FakeSmh");
                t.setDaemon(true);
                return t;
            }));
            s.start();
            server = s;
            return null;
        } catch (IOException e) {
            failure = "the fake SMH could not listen on 127.0.0.1:47821: " + e.getMessage();
            return failure;
        }
    }
}
