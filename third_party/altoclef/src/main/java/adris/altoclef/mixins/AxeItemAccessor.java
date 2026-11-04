package adris.altoclef.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import net.minecraft.world.item.AxeItem;

@Mixin(AxeItem.class)
public interface AxeItemAccessor {

    //#if MC <= 11605
    //$$ @Accessor("field_23139")
    //$$ Set<net.minecraft.block.Material> getEffectiveMaterials();
    //#endif

}
