package com.mineagent.engine.settings;

import com.mineagent.api.config.MineAgentConfig;
import com.mineagent.engine.MineAgentEngine;

/**
 * The behaviour switches in {@code mineagent.json} (SMH plan 70, Switches), read where each behaviour runs. Before the
 * config loads (and in unit tests) every switch has its default.
 */
public final class Switches {
    private Switches() {}

    /** All the switches as they are now. */
    public static MineAgentConfig.BehaviourConfig behaviour() {
        return b();
    }

    private static MineAgentConfig.BehaviourConfig b() {
        MineAgentConfig config = MineAgentEngine.getConfig();
        return config == null || config.behaviour() == null ? MineAgentConfig.BehaviourConfig.DEFAULTS : config.behaviour();
    }

    public static boolean neverBreakBuilt() { return b().neverBreakBuilt(); }
    public static boolean rememberPlaced() { return b().rememberPlaced(); }
    public static boolean closeDoors() { return b().closeDoors(); }
    /** Guard its owner while they're AFK out in the open (off unless they turn it on). */
    public static boolean afkGuard() { return b().afkGuardMe(); }
    public static long afkAfterMillis() { return Math.max(1, b().afkMinutes()) * 60_000L; }
    public static boolean sleepWithPlayers() { return b().sleepWithPlayers(); }
    public static boolean routeMemory() { return b().routeMemory(); }
    public static boolean sprint() { return b().sprint(); }
    public static boolean saveWithWorld() { return b().saveWithWorld(); }
    public static boolean swim() { return b().swim(); }
    public static boolean waterDrops() { return b().waterDrops(); }
    public static boolean bucketDescents() { return b().bucketDescents(); }
    public static boolean avoidMobs() { return b().avoidMobs(); }
    public static boolean longTrips() { return b().longTrips(); }
    /** While its owner is AFK, keep within this many blocks of them; 0: no limit (the default). */
    public static int afkStayWithin() { return b().afkStayWithin(); }
    public static boolean afkFarmLeftAlone() { return b().afkFarmLeftAlone(); }
    /** While its owner is AFK and it has nothing to do, it finds its own work (on). */
    public static boolean afkKeepBusy() { return b().afkKeepBusy(); }
    public static boolean huntForOrders() { return b().huntForOrders(); }
    public static boolean oreSightOnly() { return b().oreSightOnly(); }
    public static boolean readyCheck() { return b().readyCheck(); }
    public static int readyFood() { return b().readyFood(); }
    public static int readyTorches() { return b().readyTorches(); }
    public static boolean sparePickaxe() { return b().sparePickaxe(); }
    public static boolean escapeLavaFire() { return b().escapeLavaFire(); }
    public static boolean sleepWhenAway() { return b().sleepWhenAway(); }

    /** How much it remembers (MA5.7). */
    public static MineAgentConfig.MemoryConfig memory() {
        MineAgentConfig config = MineAgentEngine.getConfig();
        return config == null || config.memory() == null ? MineAgentConfig.MemoryConfig.DEFAULTS : config.memory();
    }
}
