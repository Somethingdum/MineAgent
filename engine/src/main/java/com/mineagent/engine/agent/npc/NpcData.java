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
    public final Map<String, Built> built = new LinkedHashMap<>();
    public String home;
    public boolean doRoutine;
    public boolean doSetGoal;
}
