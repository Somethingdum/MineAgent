// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/time/TimerGame.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.time;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import com.mineagent.engine.alto.mixins.ClientConnectionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

// Simple timer
public class TimerGame extends BaseTimer {

    private Connection lastConnection;

    public TimerGame(double intervalSeconds) {
        super(intervalSeconds);
    }

    private static double getTime(Connection connection) {
        if (connection == null) return 0;
        return (double) ((ClientConnectionAccessor) connection).getTicks() / 20.0;
    }

    @Override
    protected double currentTime() {
        if (!AltoClef.inGame()) {
            Debug.logError("Running game timer while not in game.");
            return 0;
        }
        // If we change connections, our game time will also be reset. In that case, offset our time to reflect that change.
        Connection currentConnection = null;
        if (Minecraft.getInstance().getConnection() != null) {
            currentConnection = Minecraft.getInstance().getConnection().getConnection();
        }
        if (currentConnection != lastConnection) {
            if (lastConnection != null) {
                double prevTimeTotal = getTime(lastConnection);
                Debug.logInternal("(TimerGame: New connection detected, offsetting by " + prevTimeTotal + " seconds)");
                setPrevTimeForce(getPrevTime() - prevTimeTotal);
            }
            lastConnection = currentConnection;
        }
        // Use ticks for timing. 20TPS is normal, if we go slower that's fine.
        // Adding a "mod" argument here would be hell across the board. Not happening.
        return getTime(currentConnection);
    }
}
