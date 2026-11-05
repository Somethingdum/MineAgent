// SMH plan 70, one agent: each companion's movement and doing layers, as SecondBrainEngine sets them up for an NPC
// (its README: one Automatone Baritone for the player, one AltoClefController over it; NPCSpawner and Otomaton show
// it), here for the companion's own body and ticked by the companion's own tick. Written for this mod.
package com.mineagent.engine.agent;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.entity.InputDriver;
import com.mineagent.engine.task.TaskContext;
import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.automatone.Automatone;
import com.mineagent.sbe.automatone.BaritoneProvider;
import com.mineagent.sbe.automatone.api.BaritoneAPI;
import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.common.ServerHooks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Agents {

    private Agents() {}

    /** One companion's layers, for the body it has now (a new body after a death gets new ones). */
    private record Agent(ServerPlayer body, IBaritone baritone, AltoClefController controller) {}

    private static final Map<UUID, Agent> AGENTS = new ConcurrentHashMap<>();
    /** Each body's input, for the command helper its Baritone is made with. */
    private static final Map<ServerPlayer, InputDriver> DRIVERS = new ConcurrentHashMap<>();
    private static volatile boolean initialized;

    /** At server start (each loader calls it): the game's directories, Automatone's own setup, the companions' input. */
    public static void serverStarted(MinecraftServer server) {
        ServerHooks.serverStarted(server);
        if (!initialized) {
            Automatone.onInitialize();
            initialized = true;
        }
        ServerHooks.setCommandHelpers((baritone, player) -> new CompanionCommandHelper(player, DRIVERS.get(player)));
    }

    public static void serverStopped() {
        for (UUID id : AGENTS.keySet()) remove(id);
        DRIVERS.clear();
        ServerHooks.serverStopped();
    }

    /** The companion's AltoClef, made the first time it is asked for (and again for a new body after a death). */
    public static AltoClefController of(AgentPlayer agent) {
        ServerPlayer body = TaskContext.serverPlayer(agent);
        Agent a = AGENTS.get(agent.companionId());
        if (a != null && a.body() == body) return a.controller();
        if (a != null) close(a);
        DRIVERS.put(body, TaskContext.inputDriver(agent));
        IBaritone baritone = BaritoneAPI.getProvider().getBaritone(body);
        // Its provider's map is how any of its tasks find this Baritone again (it makes a new one for a player not in it).
        BaritoneProvider.INSTANCE.playerToBaritone.put(body, baritone);
        AltoClefController controller = new AltoClefController(baritone);
        AGENTS.put(agent.companionId(), new Agent(body, baritone, controller));
        return controller;
    }

    /** The companion's AltoClef if it has one now, else null. */
    public static AltoClefController existing(UUID companionId) {
        Agent a = AGENTS.get(companionId);
        return a == null ? null : a.controller();
    }

    /** What the companion's agent is doing now: its running chain and that chain's task tree, for the scenes' traces. */
    public static String describe(UUID companionId) {
        Agent a = AGENTS.get(companionId);
        if (a == null) return "(no agent)";
        var runner = a.controller().getTaskRunner();
        var chain = runner.getCurrentTaskChain();
        var body = a.body();
        return runner.statusReport + " at " + body.blockPosition().toShortString() + " health " + (int) body.getHealth()
                + " food " + body.getFoodData().getFoodLevel() + (chain == null ? "" : " || " + chain);
    }

    /** One tick of the companion's layers (the engine calls it from the companion's own tick while they have the body). */
    public static void tick(AgentPlayer agent) {
        AltoClefController c = of(agent);
        if (c.isEnabled()) c.onTick();
    }

    /** The companion left: its layers stopped and let go of. */
    public static void remove(UUID companionId) {
        Agent a = AGENTS.remove(companionId);
        if (a != null) close(a);
    }

    private static void close(Agent a) {
        try {
            a.controller().stop();
            a.baritone().getPathingBehavior().cancelEverything();
        } finally {
            BaritoneProvider.INSTANCE.playerToBaritone.remove(a.body());
            DRIVERS.remove(a.body());
        }
    }
}
