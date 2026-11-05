// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/CraftWithMatchingWoolTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.util.CraftingRecipe;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.helpers.ItemHelper;
import java.util.function.Function;
import net.minecraft.world.item.Item;

public abstract class CraftWithMatchingWoolTask extends CraftWithMatchingMaterialsTask {

    private final Function<ItemHelper.ColorfulItems, Item> getMajorityMaterial;
    private final Function<ItemHelper.ColorfulItems, Item> getTargetItem;

    public CraftWithMatchingWoolTask(ItemTarget target, Function<ItemHelper.ColorfulItems, Item> getMajorityMaterial, Function<ItemHelper.ColorfulItems, Item> getTargetItem, CraftingRecipe recipe, boolean[] sameMask) {
        super(target, recipe, sameMask);
        this.getMajorityMaterial = getMajorityMaterial;
        this.getTargetItem = getTargetItem;
    }


    @Override
    protected Item getSpecificItemCorrespondingToMajorityResource(Item majority) {
        for (ItemHelper.ColorfulItems colorfulItem : ItemHelper.getColorfulItems()) {
            if (getMajorityMaterial.apply(colorfulItem) == majority) {
                return getTargetItem.apply(colorfulItem);
            }
        }
        return null;
    }
}
