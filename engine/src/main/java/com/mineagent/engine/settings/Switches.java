package com.mineagent.engine.settings;

import com.mineagent.api.config.MineAgentConfig;
import com.mineagent.engine.MineAgentEngine;

/**
 * The behaviour switches in {@code mineagent.json} (SMH plan 70, Switches), read where each behaviour runs. Before the
 * config loads (and in unit tests) every switch has its default.
 */
public final class Switches {
    private Switches() {}

    private static MineAgentConfig.BehaviourConfig b() {
        MineAgentConfig config = MineAgentEngine.getConfig();
        return config == null || config.behaviour() == null ? MineAgentConfig.BehaviourConfig.DEFAULTS : config.behaviour();
    }

    public static boolean neverBreakBuilt() { return b().neverBreakBuilt(); }
    public static boolean rememberPlaced() { return b().rememberPlaced(); }
    public static boolean closeDoors() { return b().closeDoors(); }
    public static boolean afkGuard() { return b().afkGuard(); }
    public static long afkAfterMillis() { return Math.max(1, b().afkMinutes()) * 60_000L; }
    public static boolean sleepWithPlayers() { return b().sleepWithPlayers(); }
    public static boolean routeMemory() { return b().routeMemory(); }
    public static boolean sprint() { return b().sprint(); }
    public static boolean saveWithWorld() { return b().saveWithWorld(); }
}
