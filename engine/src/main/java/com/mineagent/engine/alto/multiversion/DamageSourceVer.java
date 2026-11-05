// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/DamageSourceVer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

public class DamageSourceVer {


    public static DamageSource getFallDamageSource(Level world) {
        return world.damageSources().fall();
    }

}
