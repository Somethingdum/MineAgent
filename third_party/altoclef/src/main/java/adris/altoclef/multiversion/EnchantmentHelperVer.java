package adris.altoclef.multiversion;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;


public class EnchantmentHelperVer {

    @Pattern
    public boolean hasBindingCurse(ItemStack stack) {
        //#if MC >= 12100
        return EnchantmentHelper.has(stack, net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
        //#else
        //$$ return EnchantmentHelper.hasBindingCurse(stack);
        //#endif
    }

}
