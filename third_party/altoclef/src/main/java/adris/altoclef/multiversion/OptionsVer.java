package adris.altoclef.multiversion;

import net.minecraft.client.Minecraft;

public class OptionsVer {


    public static void setGamma(double value) {
        //#if MC >= 11904
        Minecraft.getInstance().options.gamma().set(value);
        //#else
        //$$ MinecraftClient.getInstance().options.gamma = value;
        //#endif
    }

    public static void setAutoJump(boolean value) {
        //#if MC >= 11904
        Minecraft.getInstance().options.autoJump().set(value);
        //#else
        //$$ MinecraftClient.getInstance().options.autoJump = value;
        //#endif
    }

}
