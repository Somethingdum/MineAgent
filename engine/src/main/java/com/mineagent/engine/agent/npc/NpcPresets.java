// SMH plan 70, one agent: the goals a companion living on its own starts with, in Mindcraft's NPC form (an NPC's profile
// lists its goals in order, data.js; do_routine keeps its day and night). They are its owner's minimum ("a hut, a
// furnace, a crafting table, some food, some fuel. And at least stone tools"), in the order the owner set ("Shelter
// first: kit order tool kit, hut, bed, the night's food, fuel"): the tools, then the hut (its blueprint holds the
// crafting table and furnace, fetched as the blocks it lacks), then food and fuel. Written for this mod.
package com.mineagent.engine.agent.npc;

public final class NpcPresets {

    private NpcPresets() {}

    /** Food in AltoClef's units (CollectFoodTask) and fuel in coal or charcoal: enough for its nights and its cooking. */
    public static final int FOOD = 30, FUEL = 8;

    public static NpcData firstDays() {
        NpcData d = new NpcData();
        d.goals.add(new NpcData.Goal("wooden_pickaxe", 1));
        d.goals.add(new NpcData.Goal("stone_pickaxe", 1));
        d.goals.add(new NpcData.Goal("stone_axe", 1));
        d.goals.add(new NpcData.Goal("stone_sword", 1));
        d.goals.add(new NpcData.Goal("stone_shovel", 1));
        d.goals.add(new NpcData.Goal("companion_hut", 1));
        d.goals.add(new NpcData.Goal("food", FOOD));
        d.goals.add(new NpcData.Goal("fuel", FUEL));
        d.doRoutine = true;
        return d;
    }
}
