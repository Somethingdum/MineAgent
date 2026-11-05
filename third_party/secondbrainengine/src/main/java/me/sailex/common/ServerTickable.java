package me.sailex.common;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

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
        ServerTickEvents.END_SERVER_TICK.register((server -> {
            if (isEnabled()) {
                onTick();
            }
        }));
    }

}
