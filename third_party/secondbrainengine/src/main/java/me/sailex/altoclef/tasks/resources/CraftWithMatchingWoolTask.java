package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import java.util.function.Function;

public abstract class CraftWithMatchingWoolTask extends CraftWithMatchingMaterialsTask {
   private final Function<ItemHelper.ColorfulItems, Item> getMajorityMaterial;
   private final Function<ItemHelper.ColorfulItems, Item> getTargetItem;

   public CraftWithMatchingWoolTask(
      ItemTarget target,
      Function<ItemHelper.ColorfulItems, Item> getMajorityMaterial,
      Function<ItemHelper.ColorfulItems, Item> getTargetItem,
      CraftingRecipe recipe,
      boolean[] sameMask
   ) {
      super(target, recipe, sameMask);
      this.getMajorityMaterial = getMajorityMaterial;
      this.getTargetItem = getTargetItem;
   }

   @Override
   protected Item getSpecificItemCorrespondingToMajorityResource(Item majority) {
      for (ItemHelper.ColorfulItems colorfulItem : ItemHelper.getColorfulItems()) {
         if (this.getMajorityMaterial.apply(colorfulItem) == majority) {
            return this.getTargetItem.apply(colorfulItem);
         }
      }

      return null;
   }
}
