// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/OptionsVer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion;

import net.minecraft.client.Minecraft;

public class OptionsVer {


    public static void setGamma(double value) {
        Minecraft.getInstance().options.gamma().set(value);
    }

    public static void setAutoJump(boolean value) {
        Minecraft.getInstance().options.autoJump().set(value);
    }

}
