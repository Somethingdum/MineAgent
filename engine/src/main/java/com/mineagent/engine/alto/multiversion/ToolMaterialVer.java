// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/ToolMaterialVer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;

public class ToolMaterialVer {

    public static int getMiningLevel(TieredItem item) {
        return getMiningLevel(item.getTier());
    }

    public static int getMiningLevel(Tier material) {
        if (material.equals(Tiers.WOOD) || material.equals(Tiers.GOLD)) {
            return 0;
        } else if (material.equals(Tiers.STONE)) {
            return 1;
        } else if (material.equals(Tiers.IRON)) {
            return 2;
        } else if (material.equals(Tiers.DIAMOND)) {
            return 3;
        } else if (material.equals(Tiers.NETHERITE)) {
            return 4;
        }
        throw new IllegalStateException("Unexpected value: " + material);
    }

}
