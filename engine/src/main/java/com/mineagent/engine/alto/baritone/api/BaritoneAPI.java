// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/api/java/baritone/api/BaritoneAPI.java
// at commit 11ea66c (branch 1.21.1). SMH: the companion that is ticking is the "primary" Baritone; settings are its own
// (each companion's walks are set apart), else Baritone's defaults.
package com.mineagent.engine.alto.baritone.api;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.baritone.Baritone;

import net.minecraft.world.entity.Entity;

public final class BaritoneAPI {

    private static final Settings DEFAULTS = new Settings();
    private static final Provider PROVIDER = new Provider();

    private BaritoneAPI() {
    }

    public static Provider getProvider() {
        return PROVIDER;
    }

    public static Settings getSettings() {
        AltoClef host = AltoClef.current();
        return host != null ? host.getClientBaritoneSettings() : DEFAULTS;
    }

    /** Baritone's IBaritoneProvider, as AltoClef's tasks use it. */
    public static final class Provider {
        public Baritone getPrimaryBaritone() {
            AltoClef host = AltoClef.current();
            return host != null ? host.getClientBaritone() : null;
        }

        public Baritone getBaritoneForPlayer(Entity player) {
            AltoClef host = AltoClef.forPlayer(player);
            return host != null ? host.getClientBaritone() : getPrimaryBaritone();
        }
    }
}
