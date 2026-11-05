// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/common/ServerTickable.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.common;


public interface ServerTickable {
    /**
     * Logic to execute on each tick
     */
    void onTick();

    boolean isEnabled();
    void setIsEnabled(boolean enabled);

    /**
     * Default implementation to register the tick handler
     */
    default void registerTickListener() {
        // SMH: ticked by the companion's own tick (the engine calls onTick), not a loader's server-tick listener.
    }

}
