package adris.altoclef.multiversion.recipemanager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

public record WrappedRecipeEntry(ResourceLocation id, Recipe<?> value) {

    //#if MC>12001
    public RecipeHolder<?> asRecipe() {
        return new RecipeHolder<Recipe<?>>(id, value);
    }
    //#else
    //$$ public Recipe<?> asRecipe(){
    //$$     return value;
    //$$ }
    //#endif

}
