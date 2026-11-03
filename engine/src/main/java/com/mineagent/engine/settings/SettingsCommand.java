package com.mineagent.engine.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;

/**
 * What the Agent settings panel may change, and what it is shown (SMH plan 69, MA2.6; the user, 2026-11-02:
 * "The mod should give an easy to understand UI panel to change settings on the agent."). The panel sends
 * {@code /mineagent settings set <key> <value>}; the command acts only on the sender's own companions, and
 * every value is checked here first. Trusted players are changed with /mineagent trust and untrust, which
 * are the owner's alone too.
 */
public final class SettingsCommand {
    public static final List<String> KEYS = List.of("mode", "persona", "curriculum", "experiments", "callrate");

    /** The settings after a change, or why it was refused. */
    public record Result(AgentSettings settings, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    /** A skill as the panel lists it: its name and its hard-coded rating. */
    public record SkillLine(String name, String score) {}

    /** A goal as the panel shows it (MA3.1): its id, its status, and its line. */
    public record GoalLine(String id, String status, String line) {}

    private SettingsCommand() {}

    public static Result apply(AgentSettings current, String key, String value) {
        AgentSettings s = current == null ? AgentSettings.DEFAULTS : current;
        String k = key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
        String v = value == null ? "" : value.strip();
        String lower = v.toLowerCase(Locale.ROOT);
        return switch (k) {
            case "persona" -> lower.equals("none") ? new Result(s.withPersona(""), null)
                    : v.length() > AgentSettings.MAX_PERSONA
                    ? new Result(s, "A personality is at most " + AgentSettings.MAX_PERSONA + " characters.")
                    : new Result(s.withPersona(v), null);
            case "curriculum" -> switch (lower) {
                case "on", "true" -> new Result(s.withCurriculum(true), null);
                case "off", "false" -> new Result(s.withCurriculum(false), null);
                default -> new Result(s, "Practice is on or off.");
            };
            case "experiments" -> List.of("low", "normal", "high").contains(lower)
                    ? new Result(s.withExperiments(lower), null)
                    : new Result(s, "Experiments are low, normal or high.");
            case "mode" -> List.of("servant", "auto", "escort", "parked", "park").contains(lower)
                    ? new Result(s.withMode(lower), null)
                    : new Result(s, "The mode is servant, auto, escort or parked.");
            case "callrate" -> List.of("calm", "normal", "busy").contains(lower)
                    ? new Result(s.withCallRate(lower), null)
                    : new Result(s, "The call rate is calm, normal or busy.");
            default -> new Result(s, "Unknown setting: " + k + ". Settings: " + String.join(", ", KEYS) + ".");
        };
    }

    /** What the panel shows, sent to the owner's client as one JSON text. No API key is ever in it. */
    public static String state(AgentSettings s, List<String> trustedNames, List<SkillLine> skills,
                               String model, String baseUrl, boolean keySet) {
        return state(s, trustedNames, skills, List.of(), model, baseUrl, keySet);
    }

    public static String state(AgentSettings s, List<String> trustedNames, List<SkillLine> skills, List<GoalLine> goals,
                               String model, String baseUrl, boolean keySet) {
        JsonObject o = new JsonObject();
        JsonArray trusted = new JsonArray();
        for (String t : trustedNames) trusted.add(t);
        o.add("trusted", trusted);
        o.addProperty("persona", s.persona());
        o.addProperty("curriculum", s.curriculum());
        o.addProperty("experiments", s.experiments());
        o.addProperty("callRate", s.callRate());
        o.addProperty("mode", s.mode());
        JsonArray list = new JsonArray();
        for (SkillLine line : skills) {
            JsonObject e = new JsonObject();
            e.addProperty("name", line.name());
            e.addProperty("score", line.score());
            list.add(e);
        }
        o.add("skills", list);
        JsonArray goalList = new JsonArray();
        for (GoalLine g : goals == null ? List.<GoalLine>of() : goals) {
            JsonObject e = new JsonObject();
            e.addProperty("id", g.id());
            e.addProperty("status", g.status());
            e.addProperty("line", g.line());
            goalList.add(e);
        }
        o.add("goals", goalList);
        o.addProperty("model", model == null ? "" : model);
        o.addProperty("baseUrl", baseUrl == null ? "" : baseUrl);
        o.addProperty("keySet", keySet);
        return o.toString();
    }
}
