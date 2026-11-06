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

    /**
     * The body's own Automatone, made and put in its provider's map as {@link #of} does: the AltoClef port's host
     * (alto/AltoClef) takes its Baritone from here (SMH plan 71, seam A, step 1.1).
     */
    public static IBaritone bodyBaritone(AgentPlayer agent) {
        ServerPlayer body = TaskContext.serverPlayer(agent);
        DRIVERS.put(body, TaskContext.inputDriver(agent));
        IBaritone baritone = BaritoneAPI.getProvider().getBaritone(body);
        BaritoneProvider.INSTANCE.playerToBaritone.put(body, baritone);
        return baritone;
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
        var body = a.body();
        var path = a.baritone().getPathingBehavior();
        StringBuilder out = new StringBuilder(runner.statusReport).append(" at ").append(body.blockPosition().toShortString())
                .append(" health ").append((int) body.getHealth()).append(" food ").append(body.getFoodData().getFoodLevel())
                .append("; baritone goal ").append(path.getGoal()).append(path.isPathing() ? ", pathing" : "")
                .append(path.getPath().map(p -> ", path of " + p.length()).orElse(", no path"))
                .append(", keys ").append(String.format("%.1f/%.1f", body.zza, body.xxa));
        // Its AltoClef rules, which Automatone asks (plan 71, step 1.5): whether it holds interaction, and how many of
        // each it carries.
        var rules = ((com.mineagent.sbe.automatone.Baritone) a.baritone()).getExtraBaritoneSettings();
        out.append("; rules: paused ").append(rules.isInteractionPaused()).append(", break ").append(rules.getBreakAvoiders().size())
                .append("+").append(rules.getBlocksToAvoidBreaking().size()).append(", place ").append(rules.getPlaceAvoiders().size())
                .append(", walk-through ").append(rules.getForceAvoidWalkThroughPredicates().size())
                .append(", protected items ").append(rules.getProtectedItems().size());
        out.append(" ||");
        // The user task tree, root to leaf (each task's own debug line), as AltoClef's own status shows it.
        Object task = field(a.controller().getUserTaskChain(), "mainTask");
        for (int depth = 0; task != null && depth < 12; depth++) {
            out.append(depth == 0 ? " " : " > ").append(task);
            task = field(task, "sub");
        }
        return out.toString();
    }

    /** A field read off one of the ported classes (its task tree and chain keep them private). */
    private static Object field(Object on, String name) {
        for (Class<?> c = on.getClass(); c != null; c = c.getSuperclass()) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(on);
            } catch (NoSuchFieldException e) {
                // up to its parent class
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
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
