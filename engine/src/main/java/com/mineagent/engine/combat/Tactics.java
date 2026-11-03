package com.mineagent.engine.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * How to fight right now (SMH plan 69, MA3.4): a stance, which mobs first, when to retreat, shield and crits.
 * The model proposes it every couple of seconds (smh/fight) and learning picks among stances by their measured
 * results; the fight never waits for either. A proposal is checked and clamped here, and the mob table's
 * "never" rules stand above it.
 *
 * @param stance        aggressive (press, crit jumps), balanced, defensive (shield first, keep 2.5 blocks),
 *                      kite (hit and step back), or retreat
 * @param targetOrder   mob types to strike first, best first (empty: the most dangerous first)
 * @param retreatHealth health at or below which it breaks off
 */
public record Tactics(String stance, List<String> targetOrder, double retreatHealth, boolean useShield, boolean crits) {
    public static final List<String> STANCES = List.of("aggressive", "balanced", "defensive", "kite", "retreat");

    public Tactics {
        stance = STANCES.contains(stance) ? stance : "balanced";
        targetOrder = targetOrder == null ? List.of() : List.copyOf(targetOrder);
    }

    /** A stance's own settings, with the flee line from the survival config as the retreat point. */
    public static Tactics of(String stance, double fleeHealth) {
        return switch (stance == null ? "" : stance) {
            case "aggressive" -> new Tactics("aggressive", List.of(), Math.max(4.0, fleeHealth - 2.0), true, true);
            case "defensive" -> new Tactics("defensive", List.of(), Math.min(16.0, fleeHealth + 2.0), true, false);
            case "kite" -> new Tactics("kite", List.of(), fleeHealth, true, false);
            case "retreat" -> new Tactics("retreat", List.of(), 20.0, true, false);
            default -> new Tactics("balanced", List.of(), fleeHealth, true, true);
        };
    }

    /**
     * The model's proposal, checked: JSON {"stance","target_order","retreat_health","shield","crits"}. The retreat
     * point is held between 4 and 16 and never below two under the flee line.
     */
    public static Optional<Tactics> parse(String reply, double fleeHealth) {
        if (reply == null) return Optional.empty();
        String text = reply.replaceAll("(?s)<think>.*?</think>", "");
        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        if (open < 0 || close <= open) return Optional.empty();
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(text.substring(open, close + 1));
            if (!e.isJsonObject()) return Optional.empty();
            o = e.getAsJsonObject();
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
        String stance = str(o, "stance").toLowerCase(Locale.ROOT);
        if (!STANCES.contains(stance)) return Optional.empty();
        Tactics base = of(stance, fleeHealth);
        List<String> order = new ArrayList<>();
        if (o.has("target_order") && o.get("target_order").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("target_order")) {
                if (e.isJsonPrimitive() && order.size() < 8) {
                    String t = e.getAsString().toLowerCase(Locale.ROOT).replaceAll("[^a-z_:]", "");
                    if (!t.isEmpty()) order.add(t.contains(":") ? t.substring(t.indexOf(':') + 1) : t);
                }
            }
        }
        double retreat = base.retreatHealth();
        if (o.has("retreat_health") && o.get("retreat_health").isJsonPrimitive() && o.get("retreat_health").getAsJsonPrimitive().isNumber()) {
            retreat = o.get("retreat_health").getAsDouble();
        }
        retreat = Math.max(Math.max(4.0, fleeHealth - 2.0), Math.min(16.0, retreat));
        boolean shield = !o.has("shield") || !o.get("shield").isJsonPrimitive() || o.get("shield").getAsBoolean();
        boolean crits = o.has("crits") && o.get("crits").isJsonPrimitive() ? o.get("crits").getAsBoolean() : base.crits();
        return Optional.of(new Tactics(stance, order, retreat, shield, crits));
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }
}
