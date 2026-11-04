package adris.altoclef.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import net.minecraft.world.item.DiggerItem;

@Mixin(DiggerItem.class)
public interface MiningToolItemAccessor {

    //#if MC <= 11605
    //$$ @Accessor("effectiveBlocks")
    //$$ Set<Block> getEffectiveBlocks();
    //#endif

}
