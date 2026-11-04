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
    /**
     * A scripted model for a scene: given a call, the assistant message to answer with (tool calls and all), or null for
     * the usual "On it.". Set by a scene and put back to null after.
     */
    static volatile java.util.function.Function<Call, JsonObject> script;

    /** An assistant message calling one tool with these arguments. */
    static JsonObject toolCall(String name, JsonObject arguments) {
        JsonObject fn = new JsonObject();
        fn.addProperty("name", name);
        fn.addProperty("arguments", arguments.toString());
        JsonObject call = new JsonObject();
        call.addProperty("id", "call_" + java.util.UUID.randomUUID().toString().substring(0, 8));
        call.addProperty("type", "function");
        call.add("function", fn);
        JsonArray calls = new JsonArray();
        calls.add(call);
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.add("content", com.google.gson.JsonNull.INSTANCE);
        message.add("tool_calls", calls);
        return message;
    }
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
                    Call call = new Call(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"), body);
                    CALLS.add(call);
                    var scripted = script;
                    JsonObject message = scripted == null ? null : scripted.apply(call);
                    boolean tools = message != null && message.has("tool_calls");
                    if (message == null) {
                        message = new JsonObject();
                        message.addProperty("role", "assistant");
                        message.addProperty("content", "On it.");
                    }
                    JsonObject choice = new JsonObject();
                    choice.addProperty("index", 0);
                    choice.add("message", message);
                    choice.addProperty("finish_reason", tools ? "tool_calls" : "stop");
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
