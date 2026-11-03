package com.mineagent.engine.chat;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The owner's plain commands, understood without a model (after the Andy Omega plan's Lexer, §8; SMH plan
 * 69). "Follow me", "stop", "come here", "auto", "status", "drop 32 cobblestone" are acted on at once, on the
 * server thread, with a short spoken answer, so nobody waits seconds for a model to hear "stop". Only whole,
 * short commands match; anything else, or an item it cannot name, goes to the model as before. A leading
 * "don't", "wait no" or "actually" is a stop, never the verb after it.
 */
public final class CommandParser {
    public enum Kind { STOP, STAY, FOLLOW, COME, MODE, STATUS, CANCEL, EAT, DROP, GUARD_HERE, GUARD_PLAYER, GUARD_OFF,
        GOAL_ADD, RESUME }

    /**
     * {@code value}: the mode for MODE, the item id for DROP, the player for GUARD_PLAYER, the goal's words for
     * GOAL_ADD. {@code count}: for DROP;
     * for GUARD_HERE, the radius asked for (null: the default).
     */
    public record Command(Kind kind, String value, Integer count) {}

    private static final Pattern NAME_PREFIX = Pattern.compile("^\\[[^\\]]{1,40}\\]:\\s*");
    private static final Pattern NEGATION = Pattern.compile("^(don'?t|do not|wait no|actually no|no stop)\\b.*");
    private static final Pattern STOP = Pattern.compile("^(stop|halt|freeze|cease|stand still|hold on|stop now|stop that|stop it|stop everything)$");
    private static final Pattern STAY = Pattern.compile("^(stay|stay here|wait|wait here|stay put)$");
    private static final Pattern FOLLOW = Pattern.compile("^(follow|follow me|come with me|stick with me|tail me|keep up)$");
    private static final Pattern COME = Pattern.compile("^(come|come here|come to me|here|over here|get over here)$");
    private static final Pattern MODE = Pattern.compile(
            "^(?:(?:go|switch to|switch|use|be|enter|mode)\\s+)?(servant|auto|escort|park|parked)(?:\\s+mode)?$");
    private static final Pattern OWN = Pattern.compile("^(on your own|do your own thing|go do your thing|be free)$");
    private static final Pattern ESCORT = Pattern.compile("^(protect me|guard me|defend me|keep me safe|escort me|cover me|watch over me)$");
    private static final Pattern STATUS = Pattern.compile("^(status|sitrep|report|what are you doing|what r u doing|what're you doing|how'?s it going)$");
    private static final Pattern CANCEL = Pattern.compile("^(cancel|abort|nevermind|never mind|forget it|cancel that|cancel the goal|drop it)$");
    private static final Pattern EAT = Pattern.compile("^(eat|eat something|eat food|have a snack|have something to eat)$");
    private static final Pattern GUARD_HERE = Pattern.compile("^(?:guard|protect|defend|watch over)\\s+(?:here|this|it|"
            + "this (?:spot|place|area|chest|door|base|house|farm|room|gate|stuff|pen|field)|"
            + "the (?:base|house|farm|chest|door|gate|area|village|pen|field))"
            + "(?:\\s+(?:radius|within|for|in a radius of)\\s+(\\d{1,2})(?:\\s+blocks?)?)?$");
    private static final Pattern GUARD_OFF = Pattern.compile("^(stop guarding|unguard|stand down|off duty|stop protecting(?: [a-z0-9_]+)?|you can stop guarding)$");
    private static final Pattern GUARD_PLAYER = Pattern.compile("^(?:guard|protect|defend|watch over)\\s+([a-z0-9_]{3,16})$");
    /** "Goal: build a castle", "new goal: get 64 logs": into the goal book (MA3.1). Any length, up to 300 characters. */
    private static final Pattern GOAL = Pattern.compile("^(?:new goal|long goal|long term goal|add goal|add a goal|goal|your goal is|your new goal is)\\s*[:,-]\\s*(.{3,300})$");
    private static final Pattern RESUME = Pattern.compile("^(resume|carry on|continue|keep going|go on|back to work|get back to work|continue the goal|resume the goal|resume your goal)$");
    private static final Pattern DROP = Pattern.compile("^(?:drop|throw|toss|chuck)\\s+(?:(\\d{1,4}|a stack of|half a stack of|all(?: your| the)?|all of your)\\s+)?([a-z][a-z _]{1,40})$");

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("cobble", "cobblestone"), Map.entry("stone", "cobblestone"), Map.entry("dirt", "dirt"),
            Map.entry("logs", "oak_log"), Map.entry("wood", "oak_log"), Map.entry("planks", "oak_planks"),
            Map.entry("sticks", "stick"), Map.entry("torches", "torch"), Map.entry("diamonds", "diamond"),
            Map.entry("iron", "iron_ingot"), Map.entry("gold", "gold_ingot"), Map.entry("coal", "coal"),
            Map.entry("diamond pick", "diamond_pickaxe"), Map.entry("iron pick", "iron_pickaxe"),
            Map.entry("stone pick", "stone_pickaxe"), Map.entry("arrows", "arrow"), Map.entry("seeds", "wheat_seeds"));

    private CommandParser() {}

    /** The command in a chat line (with or without its "[name]: " prefix), if it is one. */
    public static Optional<Command> parse(String line, String companionName) {
        String t = normalise(line, companionName);
        Matcher g = GOAL.matcher(t);
        if (g.matches()) return Optional.of(new Command(Kind.GOAL_ADD, g.group(1).strip(), null));
        if (t.isEmpty() || t.split(" ").length > 8) return Optional.empty();
        if (NEGATION.matcher(t).matches()) return Optional.of(new Command(Kind.STOP, null, null));
        if (STOP.matcher(t).matches()) return Optional.of(new Command(Kind.STOP, null, null));
        if (STAY.matcher(t).matches()) return Optional.of(new Command(Kind.STAY, null, null));
        if (FOLLOW.matcher(t).matches()) return Optional.of(new Command(Kind.FOLLOW, null, null));
        if (COME.matcher(t).matches()) return Optional.of(new Command(Kind.COME, null, null));
        Matcher m = MODE.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.MODE, m.group(1).startsWith("park") ? "parked" : m.group(1), null));
        if (OWN.matcher(t).matches()) return Optional.of(new Command(Kind.MODE, "auto", null));
        if (ESCORT.matcher(t).matches()) return Optional.of(new Command(Kind.MODE, "escort", null));
        if (GUARD_OFF.matcher(t).matches()) return Optional.of(new Command(Kind.GUARD_OFF, null, null));
        m = GUARD_HERE.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.GUARD_HERE, null, m.group(1) == null ? null : Integer.valueOf(m.group(1))));
        m = GUARD_PLAYER.matcher(t);
        if (m.matches() && !m.group(1).equals("me") && !m.group(1).equals("us")) return Optional.of(new Command(Kind.GUARD_PLAYER, m.group(1), null));
        if (STATUS.matcher(t).matches()) return Optional.of(new Command(Kind.STATUS, null, null));
        if (RESUME.matcher(t).matches()) return Optional.of(new Command(Kind.RESUME, null, null));
        if (CANCEL.matcher(t).matches()) return Optional.of(new Command(Kind.CANCEL, null, null));
        if (EAT.matcher(t).matches()) return Optional.of(new Command(Kind.EAT, null, null));
        m = DROP.matcher(t);
        if (m.matches()) {
            String q = m.group(1);
            Integer count = q == null ? Integer.valueOf(1) : q.startsWith("a stack") ? Integer.valueOf(64)
                    : q.startsWith("half") ? Integer.valueOf(32) : q.startsWith("all") ? null : Integer.valueOf(q);
            String item = itemId(m.group(2));
            // "All" of something is the model's to work out (the drop tool takes 1 to 64).
            if (item != null && count != null) return Optional.of(new Command(Kind.DROP, item, count));
        }
        return Optional.empty();
    }

    /** An item named in plain words as an item id (minecraft namespace), or null if it is not a plain name. */
    public static String itemId(String words) {
        if (words == null) return null;
        String w = words.strip().replaceAll("\\s+", " ");
        if (ALIASES.containsKey(w)) return ALIASES.get(w);
        String id = w.replace(' ', '_');
        if (!id.matches("[a-z][a-z_]{1,40}")) return null;
        // "two torches" → torch: a plain plural, when the singular is the usual item name.
        if (id.endsWith("es") && (id.endsWith("ches") || id.endsWith("shes"))) id = id.substring(0, id.length() - 2);
        else if (id.endsWith("s") && !id.endsWith("ss") && !id.endsWith("_planks") && !id.endsWith("_leaves")) id = id.substring(0, id.length() - 1);
        return id;
    }

    static String normalise(String line, String companionName) {
        if (line == null) return "";
        String t = NAME_PREFIX.matcher(line.strip()).replaceFirst("").toLowerCase(Locale.ROOT);
        String name = companionName == null ? "" : companionName.toLowerCase(Locale.ROOT).strip();
        if (!name.isEmpty()) {
            t = t.replaceFirst("^(?:hey |ok |okay |yo )?@?" + Pattern.quote(name) + "[,:!]?\\s+", "");
            t = t.replaceFirst("[,\\s]+@?" + Pattern.quote(name) + "[.!?]*$", "");
        }
        t = t.replaceAll("[.!?]+$", "").strip();
        t = t.replaceFirst("^(?:please |pls |can you |could you |would you |will you )+", "");
        t = t.replaceFirst("(?:,? please| pls)$", "");
        t = t.replaceAll("\\btwo\\b", "2").replaceAll("\\bthree\\b", "3").replaceAll("\\bfour\\b", "4")
                .replaceAll("\\bfive\\b", "5").replaceAll("\\bten\\b", "10").replaceAll("^(drop|throw|toss|chuck) (a|an|one) (?!stack)", "$1 1 ");
        return t.replaceAll("\\s+", " ").strip();
    }
}
