package adris.altoclef.multiversion;

import net.minecraft.client.Minecraft;

public class MinecraftClientVer {


    @Pattern
    private static float getTickDelta(Minecraft client) {
        //#if MC >= 12100
        return client.getTimer().getGameTimeDeltaPartialTick(true);
        //#else
        //$$ return client.getTickDelta();
        //#endif
    }

}
