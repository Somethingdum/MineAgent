// SMH plan 70: what the model proposes for the companion to do next, as Voyager's automatic curriculum proposes it
// (MineDojo/Voyager, MIT: one concrete task at a time, "Mine 3 iron ore", "Craft a stone pickaxe", "Smelt 4 iron
// ingots", "Kill 1 zombie"), done by AltoClef's own task for it and judged as Voyager's critic judges it, by what the game
// shows (the items it carries, the kills it has made), not by the model's word. Written for this mod.
package com.mineagent.engine.alto.tasks.life;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasks.entity.KillEntitiesTask;
import com.mineagent.engine.alto.tasksystem.Task;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModelWants {

    /** A proposal: its words, and what is told when it is done (true) or given up (false). */
    private record Proposal(String words, Consumer<Boolean> outcome) {}

    private static final Map<UUID, Proposal> PROPOSED = new ConcurrentHashMap<>();

    private ModelWants() {}

    /** The model's next task for a companion, replacing one not yet done (that one counts as given up). */
    public static void propose(UUID companion, String words, Consumer<Boolean> outcome) {
        if (companion == null || words == null) return;
        Proposal before = PROPOSED.put(companion, new Proposal(words, outcome));
        if (before != null && before.outcome() != null) before.outcome().accept(false);
    }

    /** Its words, or null when none is waiting. */
    public static String proposed(UUID companion) {
        Proposal p = companion == null ? null : PROPOSED.get(companion);
        return p == null ? null : p.words();
    }

    /** It is over: done (its checks passed) or given up. */
    public static void finish(UUID companion, String words, boolean done) {
        if (companion == null) return;
        Proposal p = PROPOSED.get(companion);
        if (p == null || !p.words().equals(words)) return;
        PROPOSED.remove(companion);
        if (p.outcome() != null) p.outcome().accept(done);
    }

    /** Dropped without a verdict (its life task ended, an order came). */
    public static void clear(UUID companion) {
        if (companion != null) PROPOSED.remove(companion);
    }

    private static final Pattern TASK = Pattern.compile(
            "^(mine|craft|smelt|cook|obtain|get|collect|gather|make|chop|harvest|kill|hunt|slay)\\s+(?:(\\d+|a|an|one|some|two|three|four|five)\\s+)?(.+?)[.!]?$");
    private static final Map<String, String> ORE_DROPS = Map.of("iron", "raw_iron", "gold", "raw_gold", "copper", "raw_copper",
            "coal", "coal", "diamond", "diamond", "redstone", "redstone", "lapis", "lapis_lazuli", "emerald", "emerald");

    /** What the words ask for: a kill of a kind of mob, or an item the catalogue knows; null if neither. */
    public record Ask(EntityType<?> mob, String item, int count) {}

    /** The words taken apart, with no game data: its verb, how many, and of what; null if they aren't a task. */
    public record Words(String verb, int count, String what) {}

    public static Words split(String words) {
        if (words == null) return null;
        Matcher m = TASK.matcher(words.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
        if (!m.matches()) return null;
        String what = m.group(3).strip().replaceFirst("^(the|some|more)\\s+", "");
        return what.isEmpty() ? null : new Words(m.group(1), count(m.group(2)), what);
    }

    public static Ask read(String words) {
        Words w = split(words);
        if (w == null) return null;
        String verb = w.verb(), what = w.what();
        int count = w.count();
        if (verb.equals("kill") || verb.equals("hunt") || verb.equals("slay")) {
            EntityType<?> type = mob(what);
            return type == null ? null : new Ask(type, null, count);
        }
        String item = item(verb, what);
        return item == null ? null : new Ask(null, item, count);
    }

    private static int count(String n) {
        if (n == null) return 1;
        return switch (n) {
            case "a", "an", "one", "some" -> 1;
            case "two" -> 2;
            case "three" -> 3;
            case "four" -> 4;
            case "five" -> 5;
            default -> Math.max(1, Math.min(64, Integer.parseInt(n)));
        };
    }

    private static EntityType<?> mob(String what) {
        for (String name : new String[]{what, what.replaceAll("s$", ""), what.replaceAll("es$", "")}) {
            ResourceLocation id = ResourceLocation.tryParse("minecraft:" + name.replace(' ', '_'));
            if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return BuiltInRegistries.ENTITY_TYPE.get(id);
        }
        return null;
    }

    private static String item(String verb, String what) {
        String w = what.replaceAll("\\b(wood |oak )?logs?\\b", "log").replace(' ', '_');
        // "Mine 3 iron ore" is the ore's drop, as a player mining it gets.
        if (verb.equals("mine") && w.endsWith("_ore")) {
            String metal = w.replace("deepslate_", "").replace("_ore", "");
            if (ORE_DROPS.containsKey(metal)) return ORE_DROPS.get(metal);
        }
        if (w.equals("wood") || w.equals("wood_log")) w = "log";
        for (String name : new String[]{w, w.replaceAll("s$", ""), w.replaceAll("es$", "")}) {
            if (TaskCatalogue.taskExists(name)) return name;
        }
        return null;
    }

    /** The task that does it, as AltoClef does each: the catalogue's own for an item, its kill task for a mob. */
    public static Task taskFor(AltoClef mod, Ask ask) {
        if (ask == null) return null;
        if (ask.item() != null) {
            int have = 0;
            for (Item it : TaskCatalogue.getItemMatches(ask.item())) have += mod.getItemStorage().getItemCount(it);
            return TaskCatalogue.getItemTask(ask.item(), have + ask.count());
        }
        return new KillCountTask(ask.mob(), ask.count());
    }

    /** Kills of a kind of mob, counted by the game's own statistics (its critic). */
    static final class KillCountTask extends Task {
        private final EntityType<?> type;
        private final int count;
        private int startKills = -1;

        KillCountTask(EntityType<?> type, int count) {
            this.type = type;
            this.count = count;
        }

        private int kills() {
            return AltoClef.getInstance().getPlayer().getStats().getValue(Stats.ENTITY_KILLED.get(type));
        }

        @Override
        protected void onStart() {
            if (startKills < 0) startKills = kills();
        }

        @Override
        protected Task onTick() {
            setDebugState("Killing " + (count - (kills() - startKills)) + " more " + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
            return new KillEntitiesTask(e -> e.getType() == type, LivingEntity.class);
        }

        @Override
        protected void onStop(Task interruptTask) {
        }

        @Override
        public boolean isFinished() {
            return startKills >= 0 && kills() - startKills >= count;
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof KillCountTask k && k.type == type && k.count == count;
        }

        @Override
        protected String toDebugString() {
            return "Kill " + count + " " + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        }
    }
}
