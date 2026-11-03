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
        GOAL_ADD, RESUME, AFK, BACK, PRODUCE, HOME_HERE, REMEMBER_PLACE, GO_PLACE, WHERE_PLACE, WAY_BACK, WHERE_SEEN, WHERE_PUT,
        WHAT_HAPPENED, FIND_PLACE, MINE_ORE }

    /**
     * {@code value}: the mode for MODE, the item id for DROP, the player for GUARD_PLAYER, the goal's words for
     * GOAL_ADD, the place's name for REMEMBER_PLACE, GO_PLACE ("home" for home) and WHERE_PLACE, "out" or "back" for
     * WAY_BACK. {@code count}: for DROP;
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
    /** Going AFK, or back (SMH plan 70, MA5.4). */
    private static final Pattern AFK = Pattern.compile("^(afk|brb|bbl|be right back|going afk|gonna go afk|gonna be afk|i'?m afk|i'?m going afk|back in a (?:bit|sec|second|min|minute|few)|gtg for a (?:bit|sec|second|min|minute))$");
    /**
     * "Going AFK, fill up a chest with food while I'm out": AFK, and what to do meanwhile, any length (MA5.4). A comma,
     * colon, dash or "and" after the AFK words is needed, so "afk farm is broken" is not taken for going AFK.
     */
    private static final Pattern AFK_WITH = Pattern.compile("^(?:(?:i'?m |i am )?(?:going |gonna go |gonna be |going to be |about to go )?afk"
            + "|brb|be right back|(?:i'?m )?stepping (?:away|out))(?:\\s*[,:;\u2013\u2014-]+\\s*|\\s+(?:and|so)\\s+)(.{2,300})$");
    /**
     * An order to make something (SMH plan 70, production): "fill this chest with stone bricks", "I want an entire
     * chest of stone bricks", "put 2 stacks of torches in this chest", "make me 64 torches", "I need 3 furnaces". The
     * value is the item in their words, "chest|" first when it goes in the chest they mean; the count is how many, a
     * negative number of stacks, or 0 for as many as fill the chest.
     */
    private static final String HOW_MANY = "(a couple of stacks of|a stack of|\\d{1,2} stacks of|\\d{1,4}|an?|one)";
    /** The ores it mines on its own, as people name them. */
    private static final String ORE_WORDS = "(diamonds?|iron|gold|coal|redstone|lapis(?: lazuli)?|emeralds?|copper|quartz|netherite|ancient debris|ores?)";
    /** "mine diamonds", "go mine some iron", "mine 10 iron ore", "dig for gold", "mine iron here", "mine until I say stop". */
    private static final Pattern MINE_ORE = Pattern.compile("^(?:go )?(?:mine|dig|strip mine|branch mine)(?: for)?(?: me)?(?: some)? (?:"
            + "(\\d{1,3}) )?" + ORE_WORDS + "(?: ore)?( here| at this height| on this level)?(?: until i (?:say|tell you to) stop| till i say stop| for me)?$");
    /** "get me 3 diamonds", "go get me some iron". */
    private static final Pattern GET_ORE = Pattern.compile("^(?:go )?(?:get|fetch) me (?:(\\d{1,3}) |some )?" + ORE_WORDS + "(?: ore)?$");
    /** "go mining", "mine until I say stop", "go strip mining". */
    private static final Pattern MINING = Pattern.compile("^(?:go (?:strip |branch )?mining|mine until i (?:say|tell you to) stop|start mining|keep mining)$");
    private static final Pattern FILL = Pattern.compile("^fill (?:up )?(?:(this|that|the|my|our|a|an|another|one) )?(?:chest|barrel)(?: up)? (?:with|full of) (.{2,60})$");
    private static final Pattern FILL_IT = Pattern.compile("^fill (?:it|this|that)(?: up)? (?:with|full of) (.{2,60})$");
    private static final Pattern CHEST_OF = Pattern.compile("^(?:i (?:want|need)|make me|get me|give me|i'?d like)\\s+(?:an? )?(?:(?:entire|full|whole) )?chest(?:ful)? of (.{2,60})$");
    private static final Pattern PUT_IN = Pattern.compile("^(?:put|make|craft|get)(?: me)? " + HOW_MANY + " (.{2,60}?) (?:in|into) (?:this|that|the|my|our) chest$");
    private static final Pattern MAKE = Pattern.compile("^(?:make|craft)(?: me| us)? " + HOW_MANY + " (.{2,60})$");
    private static final Pattern NEED = Pattern.compile("^i (?:need|want) " + HOW_MANY + " (.{2,60})$");
    private static final Pattern BACK = Pattern.compile("^(back|i'?m back|back now|i'?m here now|here now)$");
    // Places and the way back (SMH plan 70, MA5.6).
    /** A place's name, with the article it was said with ("the mine"): route memory matches with or without it. */
    private static final String NAME = "((?:the |my |our )?[a-z0-9][a-z0-9 '_-]{0,30})";
    private static final Pattern HOME_HERE = Pattern.compile("^(?:this is (?:my |our |the )?(?:home|base)|this is where (?:i|we) live"
            + "|set (?:my |our |the )?(?:home|base)(?: here)?|make (?:this|here) (?:my |our |the )?(?:home|base)"
            + "|(?:home|base) is here|here is (?:home|base)|(?:we|i) live here)$");
    private static final Pattern REMEMBER_PLACE = Pattern.compile("^(?:remember (?:this place|this spot|this location|this|here)(?: as)?"
            + "|call (?:this|here)(?: place| spot)?|name (?:this|here)(?: place| spot)?|mark (?:this|here)(?: place| spot)? as"
            + "|this (?:place|spot) is(?: called)?) " + NAME + "$");
    private static final Pattern GO_HOME = Pattern.compile("^(?:let'?s |lets )?(?:go|head|walk|run|return|come|take me|lead me|bring me"
            + "|take us|lead us)(?: back)?(?: to)? (?:home|(?:my |our |the )?base)$");
    private static final Pattern GO_PLACE = Pattern.compile("^(?:let'?s |lets )?(?:go|head|walk|run|return|take me|lead me|bring me"
            + "|take us|lead us)(?: back)? to " + NAME + "$");
    private static final Pattern WHERE_PLACE = Pattern.compile("^(?:where'?s|wheres|where is|where was|how far is|how far to"
            + "|how far away is) " + NAME + "$");
    private static final Pattern WAY_OUT = Pattern.compile("^(?:get (?:us|me) out(?: of here| of this cave| of the cave)?"
            + "|lead (?:me|us|the way) out(?: of here)?|take (?:me|us) out(?: of here)?|get out of (?:here|this cave|the cave)"
            + "|find (?:the|a) way out|way out)$");
    private static final Pattern WAY_BACK = Pattern.compile("^(?:go back the way (?:we|you|i) came|take (?:me|us) back the way (?:we|you) came"
            + "|back the way (?:we|you) came|retrace (?:our|your) steps|lead (?:me|us) back|take (?:me|us) back|lead the way back"
            + "|find the way back)$");
    // Asked from memory (SMH plan 70, MA5.7 and MA5.9).
    private static final Pattern WHERE_SEEN = Pattern.compile("^(?:where did you see|where have you seen|have you seen(?: any)?"
            + "|seen any|did you see(?: any)?|where can i find|where could i find) (.{2,40})$");
    private static final Pattern WHERE_PUT = Pattern.compile("^(?:where did you put|where'?d you put|where did we put"
            + "|where have you put|where did you store|where'?s the chest with|which chest has) (.{2,40})$");
    private static final Pattern WHAT_HAPPENED = Pattern.compile("^(?:what did (?:we|you) do|what happened|what have you been"
            + " (?:doing|up to)|what did you get up to)(?: (today|yesterday|while i was (?:away|gone|out)|since i left))?$");
    private static final Pattern FIND_PLACE = Pattern.compile("^(?:find|look for|search for|go find|go look for|scout for"
            + "|explore for|go and find|hunt for)(?: me| us)? (?:a |an |the |some |another )?([a-z][a-z ]{2,24})$");
    private static final Pattern EXPLORE = Pattern.compile("^(?:explore|go exploring|scout around|explore around|explore a bit)$");
    private static final Pattern WHAT_MISSED = Pattern.compile("^(?:what did i miss|catch me up|recap|fill me in)$");
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
        Matcher afk = AFK_WITH.matcher(t);
        if (afk.matches()) return Optional.of(new Command(Kind.AFK, afk.group(1).strip(), null));
        Matcher ore = MINE_ORE.matcher(t);
        if (!ore.matches()) ore = GET_ORE.matcher(t);
        if (ore.matches()) {
            boolean here = ore.groupCount() >= 3 && ore.group(3) != null;
            String what = ore.group(2) + (here ? "|here" : "");
            return Optional.of(new Command(Kind.MINE_ORE, what, ore.group(1) == null ? 0 : Integer.parseInt(ore.group(1))));
        }
        if (MINING.matcher(t).matches()) return Optional.of(new Command(Kind.MINE_ORE, "ore", 0));
        // "This chest": the one they mean. "A chest": that one if there is one by them, else one it puts down.
        Matcher make = FILL.matcher(t);
        if (make.matches()) {
            String art = make.group(1);
            boolean any = art == null || art.equals("a") || art.equals("an") || art.equals("another") || art.equals("one");
            return Optional.of(new Command(Kind.PRODUCE, (any ? "anychest|" : "chest|") + make.group(2).strip(), 0));
        }
        make = FILL_IT.matcher(t);
        if (make.matches()) return Optional.of(new Command(Kind.PRODUCE, "chest|" + make.group(1).strip(), 0));
        make = CHEST_OF.matcher(t);
        if (make.matches()) return Optional.of(new Command(Kind.PRODUCE, "anychest|" + make.group(1).strip(), 0));
        make = PUT_IN.matcher(t);
        if (make.matches()) return Optional.of(new Command(Kind.PRODUCE, "chest|" + make.group(2).strip(), howMany(make.group(1))));
        make = MAKE.matcher(t);
        if (!make.matches()) make = NEED.matcher(t);
        if (make.matches()) return Optional.of(new Command(Kind.PRODUCE, make.group(2).strip(), howMany(make.group(1))));
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
        if (AFK.matcher(t).matches()) return Optional.of(new Command(Kind.AFK, null, null));
        if (BACK.matcher(t).matches()) return Optional.of(new Command(Kind.BACK, null, null));
        if (HOME_HERE.matcher(t).matches()) return Optional.of(new Command(Kind.HOME_HERE, null, null));
        if (WAY_OUT.matcher(t).matches()) return Optional.of(new Command(Kind.WAY_BACK, "out", null));
        if (WAY_BACK.matcher(t).matches()) return Optional.of(new Command(Kind.WAY_BACK, "back", null));
        if (GO_HOME.matcher(t).matches()) return Optional.of(new Command(Kind.GO_PLACE, "home", null));
        m = REMEMBER_PLACE.matcher(t);
        if (m.matches()) {
            String name = m.group(1).strip();
            String bare = name.replaceFirst("^(?:the|my|our) ", "");
            if (bare.equals("home") || bare.equals("base")) return Optional.of(new Command(Kind.HOME_HERE, null, null));
            return Optional.of(new Command(Kind.REMEMBER_PLACE, name, null));
        }
        m = GO_PLACE.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.GO_PLACE, m.group(1).strip(), null));
        m = WHERE_SEEN.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.WHERE_SEEN, m.group(1).strip(), null));
        m = WHERE_PUT.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.WHERE_PUT, m.group(1).strip(), null));
        m = WHAT_HAPPENED.matcher(t);
        if (m.matches()) {
            String when = m.group(1) == null ? "today" : m.group(1).startsWith("yesterday") ? "yesterday"
                    : m.group(1).equals("today") ? "today" : "away";
            return Optional.of(new Command(Kind.WHAT_HAPPENED, when, null));
        }
        if (WHAT_MISSED.matcher(t).matches()) return Optional.of(new Command(Kind.WHAT_HAPPENED, "away", null));
        if (EXPLORE.matcher(t).matches()) return Optional.of(new Command(Kind.FIND_PLACE, "", null));
        m = FIND_PLACE.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.FIND_PLACE, m.group(1).strip(), null));
        m = WHERE_PLACE.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.WHERE_PLACE, m.group(1).strip(), null));
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

    /** "64" is 64, "a" one, "a stack of" one stack (as -1: a stack's size is the item's), "3 stacks of" -3. */
    static int howMany(String w) {
        if (w.matches("\\d+")) return Integer.parseInt(w);
        if (w.equals("a") || w.equals("an") || w.equals("one")) return 1;
        if (w.equals("a stack of")) return -1;
        if (w.equals("a couple of stacks of")) return -2;
        return -Integer.parseInt(w.substring(0, w.indexOf(' ')));
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
