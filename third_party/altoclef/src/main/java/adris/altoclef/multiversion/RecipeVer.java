package adris.altoclef.multiversion;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

public class RecipeVer {



    public static ItemStack getOutput(Recipe<?> recipe, Level world) {
        //#if MC >= 11904
        return recipe.getResultItem(world.registryAccess());
        //#else
        //$$ return recipe.getOutput();
        //#endif
    }


}
