package adris.altoclef.multiversion.entity;

import adris.altoclef.multiversion.Pattern;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class LivingEntityVer {


    // FIXME this should be possible with mappings, right?
    @Pattern
    private static Iterable<ItemStack> getItemsEquipped(LivingEntity entity) {
        //#if MC >= 12005
        return entity.getAllSlots();
        //#else
        //$$ return entity.getItemsEquipped();
        //#endif
    }

    @Pattern
    private static boolean isSuitableFor(Item item, BlockState state) {
        //#if MC >= 12005
        return item.getDefaultInstance().isCorrectToolForDrops(state);
        //#else
        //$$ return item.isSuitableFor(state);
        //#endif
    }

}
