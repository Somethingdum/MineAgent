// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/versionedfields/Entities.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion.versionedfields;

import net.minecraft.world.entity.Entity;

/**
 * A helper class implementing entities that are not yet supported in certain versions
 */
public class Entities {

    public static final Class<? extends Entity> UNSUPPORTED;
    public static final Class<? extends Entity> WARDEN;
    public static final Class<? extends Entity> GLOW_SQUID;

    static {
        UNSUPPORTED = VersionedFieldHelper.getUnsupportedEntityClass();

        WARDEN = net.minecraft.world.entity.monster.warden.Warden.class;

        GLOW_SQUID = net.minecraft.world.entity.GlowSquid.class;
    }



}
