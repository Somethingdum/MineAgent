package adris.altoclef.multiversion;

import net.minecraft.world.food.FoodProperties;


public class FoodComponentWrapper {


    public static FoodComponentWrapper of(FoodProperties component) {
        if (component == null) return null;

        return new FoodComponentWrapper(component);
    }

    private final FoodProperties component;

    private FoodComponentWrapper(FoodProperties component) {
        this.component = component;
    }

    public int getHunger() {
        //#if MC >= 12005
        return component.nutrition();
        //#else
        //$$ return component.getHunger();
        //#endif
    }

    public float getSaturationModifier() {
        //#if MC >= 12005
        return component.saturation();
        //#else
        //$$ return component.getSaturationModifier();
        //#endif
    }
}
