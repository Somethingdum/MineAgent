package adris.altoclef.multiversion;

import net.minecraft.client.Minecraft;

public class InGameHudVer {

    public static boolean shouldShowDebugHud() {
        //#if MC > 12001
        return Minecraft.getInstance().gui.getDebugOverlay().showDebugScreen();
        //#else
        //$$ return MinecraftClient.getInstance().options.debugEnabled;
        //#endif
    }

}
