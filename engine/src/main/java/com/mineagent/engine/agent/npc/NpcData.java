// Ported from Mindcraft (github.com/mindcraft-bots/mindcraft, MIT, Copyright (c) 2024 Kolby Nottingham) at e1c1fc3:
// src/agent/npc/data.js (NPCData): its goals in order (an item or a blueprint's name, and how many), the goal the model
// set, what it has built and where, which build is its home, and whether it keeps a daily routine and asks the model
// for goals of its own.
package com.mineagent.engine.agent.npc;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NpcData {

    /** A goal: an item and how many, or a blueprint's name (quantity 1). */
    public record Goal(String name, int quantity) {}

    /** A build: where it stands and which way it faces. */
    public record Built(String name, BlockPos position, int orientation) {}

    public final List<Goal> goals = new ArrayList<>();
    public Goal currGoal;
    /** Its builds by name, where each stands (Mindcraft's built: kept from the first step, so it goes back to the same place). */
    public final Map<String, Built> built = new LinkedHashMap<>();
    /** SMH: the builds that stand as their blueprints ask (told once each, see onBuilt). */
    public final java.util.Set<String> standing = new java.util.LinkedHashSet<>();
    public String home;
    public boolean doRoutine;
    public boolean doSetGoal;
    /** SMH: its routine found no goal to act on last time (every goal met): the model may set the next. */
    public volatile boolean idle;
    /** SMH: told when the goal the model set is met (the curriculum's verdict: what the game shows, as Voyager's critic). */
    public transient java.util.function.Consumer<Goal> onCurrGoalMet;
    /** SMH: told once when a build first stands as its blueprint asks (the companion keeps it in its journal and places). */
    public transient java.util.function.Consumer<Built> onBuilt;
    /** SMH: kills of a kind of mob when its kill goal was set, by the game's own statistics (kill goals count from there). */
    public final Map<String, Integer> killsAtStart = new LinkedHashMap<>();
}
