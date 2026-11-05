// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/DamageSourceWrapper.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion;

import net.minecraft.world.damagesource.DamageSource;

public class DamageSourceWrapper {



    public static DamageSourceWrapper of(DamageSource source) {
        if (source == null) return null;

        return new DamageSourceWrapper(source);
    }


    private final DamageSource source;

    private DamageSourceWrapper(DamageSource source) {
        this.source = source;
    }

    public DamageSource getSource() {
        return source;
    }

    public boolean bypassesArmor() {
        return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR);
    }

    public boolean bypassesShield() {
        return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD);
    }

    public boolean isOutOfWorld() {
        return source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD);
    }

}
