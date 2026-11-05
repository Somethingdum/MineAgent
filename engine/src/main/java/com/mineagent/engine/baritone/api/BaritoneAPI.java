// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/api/java/baritone/api/BaritoneAPI.java
// at commit 11ea66c (branch 1.21.1). SMH: no settings file (Baritone's defaults, then what AltoClef sets); the provider
// is made here. The settings are one set for every companion, as one client's Baritone has: they are read from its path
// search threads too.
package com.mineagent.engine.baritone.api;

import com.mineagent.engine.baritone.BaritoneProvider;

/**
 * Exposes the {@link IBaritoneProvider} instance and the {@link Settings} instance for API usage.
 *
 * @author Brady
 * @since 9/23/2018
 */
public final class BaritoneAPI {

    private static final BaritoneProvider provider = new BaritoneProvider();
    private static final Settings settings = new Settings();

    public static BaritoneProvider getProvider() {
        return BaritoneAPI.provider;
    }

    public static Settings getSettings() {
        return BaritoneAPI.settings;
    }
}
