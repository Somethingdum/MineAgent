package com.mineagent.engine.loop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the owner wants done while they're AFK, from their own words (SMH plan 70, MA5.4; the owner, Nov 3: "I should
 * just be able to tell it what I want. Like: 'Going AFK, fill up a chest with food while I'm out.'", "Going AFK, make
 * sure I dont die", "Going AFK, block yourself in, don't die"). The words after "going AFK" are split into what it
 * knows how to do itself (keep them safe, wall itself in, play it safe, stay near) and the rest, which is the job it
 * works on while they're away (then, done, it finds something else to do). Pure.
 */
public final class AfkOrders {
    /**
     * @param guardMe    keep them safe: guard them wherever they are
     * @param stayWithin keep within this many blocks of them (0: no limit)
     * @param shelter    wall itself in (after the job, if there is one) and wait for them
     * @param careful    take no risks: they said "don't die"
     * @param task       the job, in their words with "while I'm out" taken off; null when none
     */
    public record Order(boolean guardMe, int stayWithin, boolean shelter, boolean careful, String task) {
        public static final Order NONE = new Order(false, 0, false, false, null);

        public boolean any() {
            return guardMe || stayWithin > 0 || shelter || careful || task != null;
        }
    }

    private static final Pattern GUARD = Pattern.compile("^(?:(?:make sure|see that|ensure|check) (?:i|we) (?:don'?t|do not|never|won'?t) die"
            + "|keep me (?:alive|safe)|protect me|guard me|watch (?:over )?me|watch my back|look after me|keep an eye on me"
            + "|don'?t let (?:me die|anything (?:kill|hurt|get) me|me get (?:killed|hurt))"
            + "|keep (?:the )?(?:mobs|monsters) (?:off|away from) me)$");
    private static final Pattern SHELTER = Pattern.compile("^(?:(?:block|wall|box|seal|lock|brick) yourself (?:in|up)"
            + "|(?:build|make|dig) (?:yourself )?(?:a )?(?:shelter|bunker|box|hole)(?: and (?:wait|hide|stay))?"
            + "|hide|bunker down|hole up|dig in|take cover|stay (?:inside|indoors|in a shelter))$");
    private static final Pattern CAREFUL = Pattern.compile("^(?:(?:don'?t|do not|try not to) die|stay alive"
            + "|keep (?:yourself )?(?:alive|safe)|stay safe|be (?:careful|safe)|don'?t get (?:killed|hurt))$");
    private static final Pattern NEAR = Pattern.compile("^(?:stay (?:close(?: to me)?|near(?: me)?|by me|nearby|with me|around)"
            + "|don'?t (?:go|wander) (?:far|off))$");
    private static final Pattern WITHIN = Pattern.compile("^stay within (\\d{1,3})(?: blocks?)?(?: of me)?$");
    /** "While I'm out" and the like: when, not what. */
    private static final Pattern WHILE = Pattern.compile("\\s*(?:,\\s*)?(?:while i'?m (?:out|away|gone|afk|off)|while i am (?:out|away|gone|afk)"
            + "|till i'?m back|until i'?m back|until i get back|till i get back|for (?:a bit|a while|a few|a few minutes|a minute"
            + "|an hour|\\d+ (?:minutes?|mins?|hours?)))$");
    private static final Pattern CLAUSES = Pattern.compile("\\s*(?:[,;]|\\band then\\b|\\bthen\\b)\\s*");

    private AfkOrders() {}

    /** What the words after "going AFK" ask for. */
    public static Order parse(String words) {
        if (words == null) return Order.NONE;
        String t = words.toLowerCase(Locale.ROOT).strip().replaceAll("[.!?]+$", "").strip();
        boolean[] flags = new boolean[3]; // guard, shelter, careful
        int[] within = {0};
        List<String> job = new ArrayList<>();
        for (String piece : CLAUSES.split(t)) {
            String p = clean(piece);
            if (p.isEmpty()) continue;
            if (known(p, flags, within)) continue;
            // "fill a chest with food and don't die": what it knows is peeled off either end; the rest is the job.
            List<String> subs = new ArrayList<>(List.of(p.split("\\s+and\\s+")));
            while (subs.size() > 1 && known(clean(subs.get(subs.size() - 1)), flags, within)) subs.remove(subs.size() - 1);
            while (subs.size() > 1 && known(clean(subs.get(0)), flags, within)) subs.remove(0);
            String rest = clean(String.join(" and ", subs));
            if (!rest.isEmpty() && !known(rest, flags, within)) job.add(rest);
        }
        String task = job.isEmpty() ? null : String.join(", ", job);
        return new Order(flags[0], within[0], flags[1], flags[2], task);
    }

    private static String clean(String s) {
        String c = s.strip().replaceFirst("^(?:also|and|then|please|pls|so|just) ", "").strip();
        String before;
        do {
            before = c;
            c = WHILE.matcher(c).replaceAll("").strip();
        } while (!c.equals(before));
        return c;
    }

    private static boolean known(String c, boolean[] flags, int[] within) {
        if (c.isEmpty()) return false;
        if (GUARD.matcher(c).matches()) return flags[0] = true;
        if (SHELTER.matcher(c).matches()) return flags[1] = true;
        if (CAREFUL.matcher(c).matches()) return flags[2] = true;
        if (NEAR.matcher(c).matches()) {
            within[0] = 16;
            return true;
        }
        Matcher m = WITHIN.matcher(c);
        if (m.matches()) {
            within[0] = Math.max(4, Math.min(256, Integer.parseInt(m.group(1))));
            return true;
        }
        return false;
    }

    /** Their words said back from its side: "me" and "my" become "you" and "your", and the other way round. */
    public static String echo(String job) {
        if (job == null) return "";
        String[] words = job.split(" ");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            String bare = w.replaceAll("[^a-z']", "");
            String tail = w.substring(Math.min(w.length(), bare.length()));
            String swapped = switch (bare) {
                case "me" -> "you";
                case "my" -> "your";
                case "mine" -> "yours";
                case "myself" -> "yourself";
                case "your" -> "my";
                case "yours" -> "mine";
                case "yourself" -> "myself";
                default -> null;
            };
            if (out.length() > 0) out.append(' ');
            out.append(swapped != null && w.startsWith(bare) ? swapped + tail : w);
        }
        return out.toString();
    }

    /** What it says back when told they're going AFK, so they know it understood. */
    public static String reply(Order o, boolean guardSwitch) {
        List<String> parts = new ArrayList<>();
        if (o.guardMe()) parts.add("I'll keep you safe");
        else if (o.stayWithin() > 0) parts.add("I'll stay within " + o.stayWithin() + " blocks of you");
        if (o.task() != null) {
            parts.add("I'll " + echo(o.task()) + (o.shelter() ? ", then block myself in and wait for you"
                    : o.guardMe() ? "" : ", then find something else to do"));
        } else if (o.shelter()) {
            parts.add("I'll block myself in and wait for you");
        }
        if (o.careful() && !o.shelter()) parts.add("I'll play it safe");
        if (parts.isEmpty()) return guardSwitch ? "Got it. I'll keep busy and keep an eye on you." : "Got it. I'll keep busy while you're away.";
        return "Got it. While you're away, " + String.join("; ", parts) + ".";
    }
}
