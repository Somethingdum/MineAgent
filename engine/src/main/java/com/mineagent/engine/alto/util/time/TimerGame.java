// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/time/TimerGame.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.time;

import com.mineagent.engine.alto.AltoClef;

// Simple timer
// SMH: in the companion's world's game time (a client counts its connection's ticks; a server-side body has none,
// and a timer made while no host ticks runs on the first one it is read under).
public class TimerGame extends BaseTimer {

    public TimerGame(double intervalSeconds) {
        super(intervalSeconds);
    }

    @Override
    protected double currentTime() {
        AltoClef host = AltoClef.getInstance();
        if (host == null || host.getWorld() == null) {
            return 0;
        }
        // Use ticks for timing. 20TPS is normal, if we go slower that's fine.
        return host.getWorld().getGameTime() / 20.0;
    }
}
