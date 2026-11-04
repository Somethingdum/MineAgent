package adris.altoclef.multiversion;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

public class CraftingRecipeVer {


    @Pattern
    private static ItemStack getOutput(CraftingRecipe craftingRecipe) {
        //#if MC >= 11904
        return craftingRecipe.getResultItem(null);
        //#else
        //$$ return craftingRecipe.getOutput();
        //#endif
    }

}
