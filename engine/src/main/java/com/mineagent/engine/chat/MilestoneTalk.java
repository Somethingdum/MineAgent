package com.mineagent.engine.chat;

import com.mineagent.api.llm.ChatMessage;
import com.mineagent.api.llm.LLMRequest;
import com.mineagent.engine.llm.RoleRouting;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A line to the owner when something worth saying happens (SMH plan 69, MA3.3): a step of a long goal done, a skill
 * learned, health back after a close call. One smh/talk call with no tools, one sentence, at most one line of each
 * kind a minute however many events come.
 */
public final class MilestoneTalk {
    public enum Kind { MILESTONE, SKILL, RECOVERED }

    /** At most one line of a kind this often. */
    public static final long PER_KIND_MS = 60_000L;
    public static final int MAX_LINE = 200;

    private final Map<Kind, Long> last = new EnumMap<>(Kind.class);

    /** Whether a line of this kind may be said now; if so, the time is taken. */
    public synchronized boolean take(Kind kind, long nowMs) {
        Long then = last.get(kind);
        if (then != null && nowMs - then < PER_KIND_MS) return false;
        last.put(kind, nowMs);
        return true;
    }

    /** The call for the line: who it is, how it talks, what happened; no tools. */
    public static LLMRequest request(String baseUrl, String apiKey, String storedModel, double temperature,
                                     String companionName, String persona, String what) {
        return request(baseUrl, apiKey, storedModel, temperature, companionName, persona, what, null);
    }

    /** With the situation brief (MA4.2), for the owner's ears only. */
    public static LLMRequest request(String baseUrl, String apiKey, String storedModel, double temperature,
                                     String companionName, String persona, String what, String brief) {
        StringBuilder sys = new StringBuilder("You are ").append(clip(companionName, 32))
                .append(", an AI companion in a Minecraft world. Tell your owner what just happened in one short sentence, ")
                .append("in your own voice. No questions, no lists, no quotes.");
        if (persona != null && !persona.isBlank()) sys.append("\nHow you talk: ").append(clip(persona, 600));
        if (brief != null && !brief.isBlank()) sys.append("\n").append(clip(brief, 600));
        return new LLMRequest(baseUrl, apiKey, RoleRouting.modelFor(storedModel, "talk"),
                List.of(ChatMessage.system(sys.toString()), ChatMessage.user("What happened: " + clip(what, 300))),
                List.of(), List.of(), temperature, 80, null, null, null, null, Duration.ofSeconds(30), false);
    }

    /** The reply as one sentence, or null. */
    public static String sentence(String reply) {
        if (reply == null) return null;
        String t = reply.replaceAll("(?s)<think>.*?</think>", "").replaceAll("[\\r\\n]+", " ").strip();
        t = t.replaceAll("^[\"'“”]+|[\"'“”]+$", "").strip();
        if (t.isEmpty()) return null;
        int end = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && (i + 1 == t.length() || t.charAt(i + 1) == ' ')) {
                end = i + 1;
                break;
            }
        }
        if (end > 0) t = t.substring(0, end);
        return t.length() > MAX_LINE ? t.substring(0, MAX_LINE) : t;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("[\\r\\n]+", " ").strip();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
