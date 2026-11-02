package com.mineagent.engine.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A companion's own settings (SMH plan 69, MA1.7; later tasks add to it), kept with the companion as
 * JSON ({@code SavedCompanion.agentSettings}; null means the defaults).
 *
 * @param trusted players (UUID strings) whose orders the companion takes as its owner's, besides
 *                changing these settings and this list
 * @param persona a short description of how the companion talks
 * @param curriculum whether it practises on its own when nobody needs it (MA2.1; on by default)
 */
public record AgentSettings(List<String> trusted, String persona, boolean curriculum) {
    public static final AgentSettings DEFAULTS = new AgentSettings(List.of(), "", true);

    public AgentSettings(List<String> trusted, String persona) {
        this(trusted, persona, true);
    }
    /** At most this many trusted players. */
    public static final int MAX_TRUSTED = 64;
    /** At most this many characters of persona. */
    public static final int MAX_PERSONA = 600;

    public AgentSettings {
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        if (trusted != null) {
            for (String t : trusted) {
                String u = normalUuid(t);
                if (u != null && clean.size() < MAX_TRUSTED) clean.add(u);
            }
        }
        trusted = List.copyOf(clean);
        persona = persona == null ? "" : persona.strip();
        if (persona.length() > MAX_PERSONA) persona = persona.substring(0, MAX_PERSONA);
    }

    /** Whether this player is trusted. */
    public boolean isTrusted(UUID player) {
        return player != null && trusted.contains(player.toString());
    }

    public AgentSettings withTrusted(UUID player) {
        if (player == null || isTrusted(player)) return this;
        List<String> next = new ArrayList<>(trusted);
        next.add(player.toString());
        return new AgentSettings(next, persona, curriculum);
    }

    public AgentSettings withoutTrusted(UUID player) {
        if (player == null || !isTrusted(player)) return this;
        List<String> next = new ArrayList<>(trusted);
        next.remove(player.toString());
        return new AgentSettings(next, persona, curriculum);
    }

    public AgentSettings withPersona(String text) {
        return new AgentSettings(trusted, text, curriculum);
    }

    public AgentSettings withCurriculum(boolean on) {
        return new AgentSettings(trusted, persona, on);
    }

    public String toJson() {
        JsonObject obj = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String t : trusted) arr.add(t);
        obj.add("trusted", arr);
        obj.addProperty("persona", persona);
        obj.addProperty("curriculum", curriculum);
        return obj.toString();
    }

    /** The settings in a saved JSON text; the defaults for null, blank or anything unreadable. */
    public static AgentSettings fromJson(String json) {
        if (json == null || json.isBlank()) return DEFAULTS;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return DEFAULTS;
            JsonObject obj = root.getAsJsonObject();
            List<String> trusted = new ArrayList<>();
            if (obj.has("trusted") && obj.get("trusted").isJsonArray()) {
                for (JsonElement e : obj.getAsJsonArray("trusted")) {
                    if (e.isJsonPrimitive()) trusted.add(e.getAsString());
                }
            }
            String persona = obj.has("persona") && obj.get("persona").isJsonPrimitive()
                    ? obj.get("persona").getAsString() : "";
            boolean curriculum = !obj.has("curriculum") || !obj.get("curriculum").isJsonPrimitive()
                    || obj.get("curriculum").getAsBoolean();
            return new AgentSettings(trusted, persona, curriculum);
        } catch (RuntimeException e) {
            return DEFAULTS;
        }
    }

    private static String normalUuid(String s) {
        if (s == null) return null;
        try {
            return UUID.fromString(s.strip()).toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
