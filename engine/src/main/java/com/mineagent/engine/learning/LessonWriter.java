package com.mineagent.engine.learning;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineagent.api.llm.ChatMessage;
import com.mineagent.api.llm.LLMRequest;
import com.mineagent.engine.llm.RoleRouting;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lessons written by the model, and skills written up and checked (SMH plan 69, MA2.2 and MA2.3). Each is
 * one call with no tools, answered in JSON and checked here before anything is kept. What a lesson is
 * about is decided by the game: the episode's hard-coded score and what happened go in; the model only
 * puts it into words.
 */
public final class LessonWriter {
    public record Lesson(String text, String applies) {}

    public record WriteUp(String name, String description, String when, List<String> inputs) {}

    private LessonWriter() {}

    /** MA2.2: after a finished, failed or fatal episode, one smh/learn call for one lesson. */
    public static LLMRequest lessonRequest(String baseUrl, String apiKey, String storedModel, double temperature,
                                           String goal, String outcome, List<String> recentSteps, String finalState) {
        String sys = "You help an AI companion in Minecraft learn from what just happened. Write ONE short, concrete lesson "
                + "that would make the next attempt at this kind of task go better: what to do, or what to avoid. "
                + "Say \"general\" if it holds in any world, \"world\" if it is about this world's places or players. "
                + "Answer with JSON only: {\"lesson\":\"...\",\"applies\":\"general|world\"}.";
        StringBuilder user = new StringBuilder()
                .append("Task: ").append(clip(goal, 240)).append('\n')
                .append("How it went (measured by the game): ").append(clip(outcome, 240)).append('\n')
                .append("Last steps:\n");
        for (String step : recentSteps) user.append("- ").append(clip(step, 240)).append('\n');
        user.append("At the end: ").append(clip(finalState, 600));
        return call(baseUrl, apiKey, RoleRouting.modelFor(storedModel, "learn"), temperature, sys, user.toString(), 300);
    }

    public static Optional<Lesson> parseLesson(String reply) {
        JsonObject o = json(reply);
        if (o == null) return Optional.empty();
        String text = str(o, "lesson");
        if (text.length() < 10 || text.length() > 300) return Optional.empty();
        String applies = "general".equalsIgnoreCase(str(o, "applies")) ? "general" : "world";
        return Optional.of(new Lesson(text, applies));
    }

    /** MA2.3: a verified step list, written up as a named skill. */
    public static LLMRequest writeUpRequest(String baseUrl, String apiKey, String storedModel, double temperature,
                                            String goal, String stepsJson) {
        String sys = "You name and describe a skill an AI companion in Minecraft just learned, from the exact steps it took. "
                + "The description says what the steps do; \"when\" says when to use it; inputs are the things that change "
                + "between uses (places, items, counts). Describe only what the steps really do. "
                + "Answer with JSON only: {\"name\":\"snake_case\",\"description\":\"...\",\"when\":\"...\",\"inputs\":[\"...\"]}.";
        String user = "Task it was for: " + clip(goal, 240) + "\nSteps: " + clip(stepsJson, 3000);
        return call(baseUrl, apiKey, RoleRouting.modelFor(storedModel, "learn"), temperature, sys, user, 300);
    }

    public static Optional<WriteUp> parseWriteUp(String reply) {
        JsonObject o = json(reply);
        if (o == null) return Optional.empty();
        String name = str(o, "name").toLowerCase(java.util.Locale.ROOT);
        String description = str(o, "description");
        String when = str(o, "when");
        if (!name.matches("[a-z][a-z0-9_]{2,47}") || description.length() < 8 || description.length() > 300
                || when.length() > 300) return Optional.empty();
        List<String> inputs = new ArrayList<>();
        JsonElement in = o.get("inputs");
        if (in != null && in.isJsonArray()) {
            for (JsonElement e : (JsonArray) in) {
                if (e.isJsonPrimitive() && inputs.size() < 12) inputs.add(clip(e.getAsString(), 60));
            }
        }
        return Optional.of(new WriteUp(name, description, when.isBlank() ? description : when, inputs));
    }

    /** MA2.3: the second read. A review checks that the write-up says what the steps do. */
    public static LLMRequest reviewRequest(String baseUrl, String apiKey, String storedModel, double temperature,
                                           WriteUp writeUp, String stepsJson) {
        String sys = "You check another model's work. Does this description match these steps exactly, claiming nothing "
                + "they do not do? Answer with JSON only: {\"ok\":true|false,\"fix\":\"...\"}.";
        String user = "Name: " + writeUp.name() + "\nDescription: " + writeUp.description() + "\nWhen: " + writeUp.when()
                + "\nSteps: " + clip(stepsJson, 3000);
        return call(baseUrl, apiKey, RoleRouting.modelFor(storedModel, "review"), temperature, sys, user, 200);
    }

    /** The review's verdict: true only for an explicit ok. */
    public static boolean parseReview(String reply) {
        JsonObject o = json(reply);
        return o != null && o.has("ok") && o.get("ok").isJsonPrimitive() && o.get("ok").getAsJsonPrimitive().isBoolean()
                && o.get("ok").getAsBoolean();
    }

    private static LLMRequest call(String baseUrl, String apiKey, String model, double temperature,
                                   String system, String user, int maxTokens) {
        List<ChatMessage> messages = List.of(ChatMessage.system(system), ChatMessage.user(user));
        return new LLMRequest(baseUrl, apiKey, model, messages, List.of(), List.of(), temperature, maxTokens,
                null, null, null, null, Duration.ofSeconds(120), false);
    }

    private static JsonObject json(String reply) {
        if (reply == null) return null;
        String text = reply.replaceAll("(?s)<think>.*?</think>", "").strip();
        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        if (open < 0 || close <= open) return null;
        try {
            JsonElement e = JsonParser.parseString(text.substring(open, close + 1));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString().replaceAll("[\\r\\n]+", " ").strip() : "";
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
