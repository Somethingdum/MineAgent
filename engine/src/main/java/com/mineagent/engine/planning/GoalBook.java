package com.mineagent.engine.planning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The goal book (SMH plan 69, MA3.1 and MA3.2): goals that outlast a session, worked toward one at a time, and
 * standing stock orders ("keep 64 torches") it tops up when it has nothing else to do, for long unattended runs.
 *
 * <p>A goal is done only when code agrees (the Andy Omega plan: success only by predicates). The model proposes
 * the next step and says when it thinks the goal is done; if the goal names amounts of items ("get 64 oak logs"),
 * those are counted in the inventory, and a goal with an amount still short is not done whatever the model says.
 * Pure: the loop gives it the time, the counts and the model's replies.
 */
public final class GoalBook {
    public enum Status { ACTIVE, PAUSED, DONE, DROPPED }

    /** An amount the goal names, checked by counting. {@code item}: an item id without its namespace. */
    public record Need(String item, int count) {}

    public record Milestone(String text, long tick) {}

    /**
     * {@code steps}: steps the goal loop has taken on it. {@code lastNext}, {@code repeats}: the step last proposed,
     * and how many times running it has come back the same (it is told to try another way). {@code shortDone}: times
     * in a row the model called it done while the count said short.
     */
    public record Goal(String id, String text, String setBy, long createdTick, Status status, List<Milestone> milestones,
                       String progress, List<Need> needs, int steps, String lastNext, int repeats, int shortDone) {
        public Goal {
            milestones = milestones == null ? List.of() : List.copyOf(milestones);
            needs = needs == null ? List.of() : List.copyOf(needs);
            progress = progress == null ? "" : progress;
            lastNext = lastNext == null ? "" : lastNext;
        }

        Goal with(Status s) {
            return new Goal(id, text, setBy, createdTick, s, milestones, progress, needs, steps, lastNext, repeats, shortDone);
        }
    }

    /** A standing order: keep at least {@code count} of {@code item}. */
    public record Stock(String item, int count) {}

    public record State(List<Goal> goals, List<Stock> stock, int nextId) {}

    /** What the goal loop does after a step is verified. {@code claimedShort}: the model said done; the count said no. */
    public record Step(boolean done, String next, boolean claimedShort) {}

    public static final int MAX_GOALS = 32;
    public static final int MAX_MILESTONES = 64;
    /** A goal still not done after this many steps is paused and the owner told (it is not getting there). */
    public static final int MAX_STEPS = 200;
    public static final int MAX_STOCK = 16;
    /** Goals active or queued, at most. */
    public static final int MAX_OPEN = 16;
    /** Called done this many times in a row while short: paused, and the owner asked (the count may be misreading). */
    public static final int MAX_SHORT_DONE = 5;
    public static final int MAX_TEXT = 300;

    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\w.])(\\d{1,4})\\s+(?:x\\s+)?([a-z][a-z_]*(?:\\s+[a-z][a-z_]*){0,2})");
    /** Amounts count only after a verb of having (get, mine, craft...), up to a verb that uses them up or a new clause. */
    private static final Pattern HAVING = Pattern.compile(
            "\\b(get|gather|collect|mine|dig|chop|cut|harvest|obtain|find|have|make|craft|bring|farm|fish|keep|stock up on)\\b");
    private static final Pattern SPAN_END = Pattern.compile(
            "\\b(then|into|to build|to make|to craft|for building|and build|and place|and smelt|smelt|cook|use|place|build)\\b|[.;!?]");
    /** What mining a block leaves in the inventory. */
    private static final Map<String, String> DROPS = Map.ofEntries(
            Map.entry("iron_ore", "raw_iron"), Map.entry("deepslate_iron_ore", "raw_iron"),
            Map.entry("gold_ore", "raw_gold"), Map.entry("deepslate_gold_ore", "raw_gold"),
            Map.entry("copper_ore", "raw_copper"), Map.entry("deepslate_copper_ore", "raw_copper"),
            Map.entry("coal_ore", "coal"), Map.entry("deepslate_coal_ore", "coal"),
            Map.entry("diamond_ore", "diamond"), Map.entry("deepslate_diamond_ore", "diamond"),
            Map.entry("emerald_ore", "emerald"), Map.entry("redstone_ore", "redstone"),
            Map.entry("lapis_ore", "lapis_lazuli"), Map.entry("nether_quartz_ore", "quartz"),
            Map.entry("stone", "cobblestone"), Map.entry("grass_block", "dirt"));
    /** Families: any log or stem, any planks. The loop counts them (item ids ending in the suffix). */
    public static final String LOGS = "#logs";
    public static final String PLANKS = "#planks";

    private final List<Goal> goals = new ArrayList<>();
    private final List<Stock> stock = new ArrayList<>();
    private int nextId = 1;

    /**
     * A new goal; active if none is, else queued (paused) behind the active one. Amounts in its words become
     * needs when {@code isItem} knows the item.
     */
    public synchronized Goal add(String text, String setBy, long tick, Predicate<String> isItem) {
        String t = clip(text);
        if (t.isEmpty()) throw new IllegalArgumentException("a goal needs words");
        long open = goals.stream().filter(g -> g.status() == Status.ACTIVE || g.status() == Status.PAUSED).count();
        if (open >= MAX_OPEN) throw new IllegalStateException("the goal book is full (" + MAX_OPEN + " open goals)");
        Status s = active().isPresent() ? Status.PAUSED : Status.ACTIVE;
        Goal g = new Goal("g" + nextId++, t, clip(setBy), tick, s, List.of(), "", needsIn(t, isItem), 0, "", 0, 0);
        goals.add(g);
        prune();
        return g;
    }

    public synchronized Optional<Goal> active() {
        return goals.stream().filter(g -> g.status() == Status.ACTIVE).findFirst();
    }

    public synchronized Optional<Goal> get(String id) {
        return goals.stream().filter(g -> g.id().equalsIgnoreCase(id == null ? "" : id.strip())).findFirst();
    }

    /** Active first, then the queue oldest first, then what is finished, newest first. */
    public synchronized List<Goal> list() {
        List<Goal> out = new ArrayList<>(goals);
        out.sort(Comparator.comparingInt((Goal g) -> g.status().ordinal())
                .thenComparingLong(g -> g.status().ordinal() >= Status.DONE.ordinal() ? -g.createdTick() : g.createdTick()));
        return out;
    }

    /** Make {@code id} the active goal; the one active before is paused with its progress kept. */
    public synchronized boolean switchTo(String id) {
        Optional<Goal> target = get(id);
        if (target.isEmpty() || target.get().status() == Status.DONE || target.get().status() == Status.DROPPED) return false;
        if (target.get().status() == Status.ACTIVE) return true;
        active().ifPresent(a -> replace(a.with(Status.PAUSED)));
        replace(target.get().with(Status.ACTIVE));
        return true;
    }

    /** Drop a goal; if it was the active one, the next in the queue becomes active. The new active goal, if any. */
    public synchronized Optional<Goal> drop(String id) {
        Optional<Goal> g = get(id);
        if (g.isEmpty() || g.get().status() == Status.DONE || g.get().status() == Status.DROPPED) return active();
        boolean wasActive = g.get().status() == Status.ACTIVE;
        replace(g.get().with(Status.DROPPED));
        return wasActive ? promoteNext() : active();
    }

    /** Pause the active goal (the owner said stop); nothing becomes active. */
    public synchronized void pauseActive() {
        active().ifPresent(a -> replace(a.with(Status.PAUSED)));
    }

    /** A step of the active goal was verified. */
    public synchronized void milestone(String text, long tick) {
        active().ifPresent(a -> {
            List<Milestone> ms = new ArrayList<>(a.milestones());
            ms.add(new Milestone(clip(text), tick));
            while (ms.size() > MAX_MILESTONES) ms.remove(0);
            replace(new Goal(a.id(), a.text(), a.setBy(), a.createdTick(), a.status(), ms, a.progress(), a.needs(),
                    a.steps(), a.lastNext(), a.repeats(), a.shortDone()));
        });
    }

    /**
     * The loop is about to run {@code next} on the active goal: counted, a repeat noticed, and a "done" the count
     * refused ({@code claimedShort}) counted too. Past {@link #MAX_STEPS} steps, or {@link #MAX_SHORT_DONE} refused
     * "done"s in a row, the goal is paused and nothing is returned: the loop tells the owner.
     */
    public synchronized Optional<Goal> stepping(String next, String progress, boolean claimedShort) {
        Optional<Goal> a = active();
        if (a.isEmpty()) return a;
        Goal g = a.get();
        String n = clip(next);
        int repeats = normal(n).equals(normal(g.lastNext())) ? g.repeats() + 1 : 0;
        int shortDone = claimedShort ? g.shortDone() + 1 : 0;
        Goal updated = new Goal(g.id(), g.text(), g.setBy(), g.createdTick(), g.status(), g.milestones(),
                progress == null ? g.progress() : clip(progress), g.needs(), g.steps() + 1, n, repeats, shortDone);
        if (updated.steps() > MAX_STEPS || shortDone >= MAX_SHORT_DONE) {
            replace(updated.with(Status.PAUSED));
            return Optional.empty();
        }
        replace(updated);
        return Optional.of(updated);
    }

    /** The owner closes a goal the count would not ("goal done g2"). */
    public synchronized Optional<Goal> markDone(String id) {
        Optional<Goal> g = get(id);
        if (g.isEmpty() || g.get().status() == Status.DONE || g.get().status() == Status.DROPPED) return active();
        boolean wasActive = g.get().status() == Status.ACTIVE;
        replace(g.get().with(Status.DONE));
        return wasActive ? promoteNext() : active();
    }

    /** The active goal is done; the next in the queue becomes active. The new active goal, if any. */
    public synchronized Optional<Goal> complete() {
        active().ifPresent(a -> replace(a.with(Status.DONE)));
        return promoteNext();
    }

    /** The active goal's amounts still short, given what it has ({@code have}: item id → count). */
    public synchronized List<Need> unmet(Function<String, Integer> have) {
        Optional<Goal> a = active();
        if (a.isEmpty()) return List.of();
        return unmet(a.get().needs(), have);
    }

    static List<Need> unmet(List<Need> needs, Function<String, Integer> have) {
        List<Need> out = new ArrayList<>();
        for (Need n : needs) {
            Integer h = have.apply(n.item());
            int got = h == null ? 0 : h;
            if (got < n.count()) out.add(new Need(n.item(), n.count() - got));
        }
        return out;
    }

    /**
     * The model's word after a verified step, checked: {"done":bool,"next":"..."}; a done goal whose amounts are
     * short is not done, and the next step is to get what is short.
     */
    public static Optional<Step> parseStep(String reply, List<Need> shortBy) {
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
        boolean done = o.has("done") && o.get("done").isJsonPrimitive() && o.get("done").getAsBoolean();
        String next = o.has("next") && o.get("next").isJsonPrimitive() ? clip(o.get("next").getAsString()) : "";
        if (done && shortBy != null && !shortBy.isEmpty()) {
            return Optional.of(new Step(false, "Still short: " + describe(shortBy), true));
        }
        if (!done && next.isEmpty()) return Optional.empty();
        return Optional.of(new Step(done, next, false));
    }

    /**
     * Amounts in a goal's words ("get 64 oak logs and 20 iron ingots"), as needs, the items checked by {@code isItem}.
     * Only what the goal means to end up holding: amounts after a verb of having, up to a verb that uses them up
     * ("smelt 20 iron ore" names no need). Mined ores count as what they drop; "logs" and "planks" as any kind.
     */
    static List<Need> needsIn(String text, Predicate<String> isItem) {
        List<Need> out = new ArrayList<>();
        if (text == null || isItem == null) return out;
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher verb = HAVING.matcher(lower);
        while (verb.find() && out.size() < 8) {
            boolean mining = verb.group(1).equals("mine") || verb.group(1).equals("dig");
            int from = verb.end();
            Matcher end = SPAN_END.matcher(lower);
            int to = end.find(from) ? end.start() : lower.length();
            Matcher m = AMOUNT.matcher(lower.substring(from, to));
            while (m.find() && out.size() < 8) {
                int count = Integer.parseInt(m.group(1));
                if (count <= 0) continue;
                String id = itemOf(m.group(2).split("\\s+"), mining, isItem);
                if (id != null && out.stream().noneMatch(n -> n.item().equals(id))) out.add(new Need(id, count));
            }
        }
        return out;
    }

    /** The longest run of words that names an item ("oak logs", then "oak"), as what ends up in the inventory. */
    private static String itemOf(String[] words, boolean mining, Predicate<String> isItem) {
        for (int n = words.length; n >= 1; n--) {
            String phrase = String.join(" ", java.util.Arrays.copyOf(words, n));
            if (phrase.matches("(logs?|wood|woods)")) return LOGS;
            if (phrase.matches("planks?|wooden planks|wood planks")) return PLANKS;
            String id = com.mineagent.engine.chat.CommandParser.itemId(phrase);
            if (id == null) continue;
            if (id.equals("oak_log") && !phrase.startsWith("oak")) return LOGS; // the parser's alias for "logs"
            // An ore comes out as its drop however it is said ("get 20 iron ore": raw iron); stone and grass only when mined.
            if (DROPS.containsKey(id) && (mining || id.endsWith("_ore"))) return DROPS.get(id);
            if (isItem.test(id)) return id;
        }
        return null;
    }

    // ── Standing orders ──

    /** Keep at least {@code count} of {@code item}; 0 removes the order. */
    public synchronized boolean setStock(String item, int count) {
        if (item == null || item.isBlank()) return false;
        String id = item.strip().toLowerCase(Locale.ROOT);
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        final String key = id;
        stock.removeIf(s -> s.item().equals(key));
        if (count <= 0) return true;
        if (stock.size() >= MAX_STOCK) return false;
        stock.add(new Stock(key, Math.min(count, 64 * 36)));
        return true;
    }

    public synchronized List<Stock> stock() {
        return List.copyOf(stock);
    }

    /** The first standing order it is short of, with how many it has. */
    public synchronized Optional<Map.Entry<Stock, Integer>> dueStock(Function<String, Integer> have) {
        for (Stock s : stock) {
            Integer h = have.apply(s.item());
            int got = h == null ? 0 : h;
            if (got < s.count()) return Optional.of(Map.entry(s, got));
        }
        return Optional.empty();
    }

    // ── Saving ──

    public synchronized State exportState() {
        return new State(List.copyOf(goals), List.copyOf(stock), nextId);
    }

    public synchronized void importState(State s) {
        goals.clear();
        stock.clear();
        if (s == null) return;
        if (s.goals() != null) {
            boolean seenActive = false;
            for (Goal g : s.goals()) {
                if (g == null || g.id() == null || g.text() == null || g.status() == null) continue;
                // Only one active goal, whatever the file says.
                if (g.status() == Status.ACTIVE) {
                    if (seenActive) g = g.with(Status.PAUSED);
                    seenActive = true;
                }
                goals.add(g);
            }
        }
        if (s.stock() != null) {
            for (Stock st : s.stock()) if (st != null && st.item() != null && st.count() > 0 && stock.size() < MAX_STOCK) stock.add(st);
        }
        nextId = Math.max(s.nextId(), 1);
        for (Goal g : goals) {
            try {
                nextId = Math.max(nextId, Integer.parseInt(g.id().substring(1)) + 1);
            } catch (RuntimeException ignored) {
                // a hand-edited id
            }
        }
        prune();
    }

    /** One line a person reads: "g2 (active) get 64 oak logs: 3 steps, short 40 oak log". */
    public static String line(Goal g, List<Need> shortBy) {
        StringBuilder b = new StringBuilder(g.id()).append(" (").append(g.status().name().toLowerCase(Locale.ROOT)).append(") ")
                .append(g.text());
        if (g.steps() > 0) b.append(": ").append(g.steps()).append(g.steps() == 1 ? " step" : " steps");
        if (!g.progress().isEmpty()) b.append(", ").append(g.progress());
        if (shortBy != null && !shortBy.isEmpty()) b.append(", short ").append(describe(shortBy));
        return b.toString();
    }

    static String describe(List<Need> needs) {
        List<String> parts = new ArrayList<>();
        for (Need n : needs) parts.add(n.count() + " " + n.item().replace('_', ' '));
        return String.join(", ", parts);
    }

    private Optional<Goal> promoteNext() {
        Optional<Goal> next = goals.stream().filter(g -> g.status() == Status.PAUSED)
                .min(Comparator.comparingLong(Goal::createdTick));
        next.ifPresent(g -> replace(g.with(Status.ACTIVE)));
        return next.map(g -> g.with(Status.ACTIVE));
    }

    private void replace(Goal g) {
        for (int i = 0; i < goals.size(); i++) {
            if (goals.get(i).id().equals(g.id())) {
                goals.set(i, g);
                return;
            }
        }
    }

    /** Keep the newest finished goals when the book is full; never an active or queued one. */
    private void prune() {
        while (goals.size() > MAX_GOALS) {
            Optional<Goal> oldest = goals.stream().filter(g -> g.status() == Status.DONE || g.status() == Status.DROPPED)
                    .min(Comparator.comparingLong(Goal::createdTick));
            if (oldest.isEmpty()) break;
            goals.remove(oldest.get());
        }
    }

    private static String normal(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    private static String clip(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\r\\n]+", " ").strip();
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }
}
