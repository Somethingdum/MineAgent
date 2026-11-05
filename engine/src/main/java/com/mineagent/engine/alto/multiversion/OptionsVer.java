// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/OptionsVer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion;


public class OptionsVer {


    public static void setGamma(double value) {
        // SMH: a server-side body has no screen brightness.
    }

    public static void setAutoJump(boolean value) {
        // SMH: the companion jumps only when its walk says so; there is no auto-jump to set.
    }

}
