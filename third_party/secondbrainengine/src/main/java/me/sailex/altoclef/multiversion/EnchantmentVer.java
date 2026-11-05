package me.sailex.altoclef.multiversion;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import java.util.Optional;

public class EnchantmentVer {

    
    public static int getEnchantmentLevel(ResourceKey<Enchantment> enchantment, ItemStack stack, MinecraftServer server) {
        Registry<Enchantment> enchantmentRegistry = /^? >=1.21.8 {^/
                /^server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                ^//^?} else {^/ server.getRegistryManager().get(RegistryKeys.ENCHANTMENT); /^?}^/
        Optional<RegistryEntry.Reference<Enchantment>> entry = enchantmentRegistry.getEntry(enchantment.getValue());
        return EnchantmentHelper.getLevel(entry.get(), stack);
    }
    

}
