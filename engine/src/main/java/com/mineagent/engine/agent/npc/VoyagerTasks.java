// SMH plan 70, one agent: a task in the form Voyager's curriculum proposes it (MineDojo/Voyager, MIT: its curriculum
// prompt asks for "Mine [quantity] [block]", "Craft [quantity] [item]", "Smelt [quantity] [item]", "Kill [quantity]
// [mob]", "Cook [quantity] [food]", "Equip [item]"; its first is "Mine 1 wood log") read as one of the routine's goals
// (Mindcraft's curr_goal: a name and a quantity): an item AltoClef's catalogue knows (an ore mined is its drop, "wood
// log" any log), or "kill:<mob>". Written for this mod, after the earlier ModelWants reading of the same form.
package com.mineagent.engine.agent.npc;

import com.mineagent.sbe.altoclef.TaskCatalogue;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoyagerTasks {

    private VoyagerTasks() {}

    private static final Pattern TASK = Pattern.compile(
            "^(mine|craft|smelt|cook|obtain|get|collect|gather|make|chop|harvest|kill|hunt|slay|equip)\\s+(?:(\\d+|a|an|one|some|two|three|four|five)\\s+)?(.+?)[.!]?$");
    private static final Map<String, String> ORE_DROPS = Map.of("iron", "raw_iron", "gold", "raw_gold", "copper", "raw_copper",
            "coal", "coal", "diamond", "diamond", "redstone", "redstone", "lapis", "lapis_lazuli", "emerald", "emerald");

    /** The routine's goal for a curriculum task, or null if it reads as neither an item nor a kill. */
    public static NpcData.Goal goal(String task) {
        if (task == null) return null;
        Matcher m = TASK.matcher(task.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
        if (!m.matches()) return null;
        String verb = m.group(1);
        int count = count(m.group(2));
        String what = m.group(3).strip().replaceFirst("^(the|some|more)\\s+", "");
        if (what.isEmpty()) return null;
        if (verb.equals("kill") || verb.equals("hunt") || verb.equals("slay")) {
            String mob = mob(what);
            return mob == null ? null : new NpcData.Goal("kill:" + mob, count);
        }
        String item = item(verb, what);
        return item == null ? null : new NpcData.Goal(item, count);
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

    private static String mob(String what) {
        for (String name : new String[]{what, what.replaceAll("s$", ""), what.replaceAll("es$", "")}) {
            ResourceLocation id = ResourceLocation.tryParse("minecraft:" + name.replace(' ', '_'));
            if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return id.getPath();
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
}
