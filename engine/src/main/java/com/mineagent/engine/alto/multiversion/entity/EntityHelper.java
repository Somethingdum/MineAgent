// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/entity/EntityHelper.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion.entity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;

public class EntityHelper {

    public static boolean isInNetherPortal(Entity entity) {
       // SMH: which portal it stands in, from the block it stands in (AltoClef reads the portal process's private field
       // through a mixin accessor).
       return (entity.portalProcess != null && entity.portalProcess.isInsidePortalThisTick()
               && (entity.level().getBlockState(entity.blockPosition()).is(Blocks.NETHER_PORTAL)
                   || entity.level().getBlockState(entity.blockPosition().above()).is(Blocks.NETHER_PORTAL)))
               || entity.getPortalCooldown() > 0;
    }


}
