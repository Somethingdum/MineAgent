// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/MiningRequirement.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util;

import com.mineagent.engine.alto.Debug;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public enum MiningRequirement implements Comparable<MiningRequirement> {
    HAND(Items.AIR), WOOD(Items.WOODEN_PICKAXE), STONE(Items.STONE_PICKAXE), IRON(Items.IRON_PICKAXE), DIAMOND(Items.DIAMOND_PICKAXE);

    private final Item _minPickaxe;

    MiningRequirement(Item minPickaxe) {
        _minPickaxe = minPickaxe;
    }

    // FIXME this doesnt work for cobwebs because they are broken with shears...
    public static MiningRequirement getMinimumRequirementForBlock(Block block) {
        if (block.defaultBlockState().requiresCorrectToolForDrops()) {
            for (MiningRequirement req : MiningRequirement.values()) {
                if (req == MiningRequirement.HAND) continue;
                Item pick = req.getMinimumPickaxe();
                if (pick.getDefaultInstance().isCorrectToolForDrops(block.defaultBlockState())) {
                    return req;
                }
            }
            Debug.logWarning("Failed to find ANY effective tool against: " + block + ". I assume netherite is not required anywhere, so something else probably went wrong.");
            return MiningRequirement.DIAMOND;
        }
        return MiningRequirement.HAND;
    }

    public Item getMinimumPickaxe() {
        return _minPickaxe;
    }

}
