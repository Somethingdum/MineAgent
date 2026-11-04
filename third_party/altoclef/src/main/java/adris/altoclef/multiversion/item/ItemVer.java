package adris.altoclef.multiversion.item;

import adris.altoclef.multiversion.FoodComponentWrapper;
import adris.altoclef.multiversion.Pattern;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public class ItemVer {

    public static FoodComponentWrapper getFoodComponent(Item item) {
        //#if MC >=12005
        return FoodComponentWrapper.of(item.components().get(net.minecraft.core.component.DataComponents.FOOD));
        //#else
        //$$ return FoodComponentWrapper.of(item.getFoodComponent());
        //#endif
    }

    public static boolean isFood(ItemStack stack) {
        return isFood(stack.getItem());
    }

    public static boolean hasCustomName(ItemStack stack) {
        //#if MC >= 12005
        return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        //#else
        //$$ return stack.hasCustomName();
        //#endif
    }

    public static boolean isFood(Item item) {
        //#if MC >=12005
        return item.components().has(net.minecraft.core.component.DataComponents.FOOD);
        //#else
        //$$ return item.isFood();
        //#endif
    }

    @Pattern
    private static boolean isSuitableFor(Item item, BlockState state) {
        //#if MC >= 11701
        return item.getDefaultInstance().isCorrectToolForDrops(state);
        //#else
        //$$ return adris.altoclef.multiversion.item.ItemHelper.isSuitableFor(item, state);
        //#endif
    }

    // the fact that this works is insane...
    @Pattern
    private static Item RAW_GOLD() {
        //#if MC >= 11701
        return Items.RAW_GOLD;
        //#else
        //$$ return Items.GOLD_ORE;
        //#endif
    }

    @Pattern
    private static Item RAW_IRON() {
        //#if MC >= 11701
        return Items.RAW_IRON;
        //#else
        //$$ return Items.IRON_ORE;
        //#endif
    }


}
