package me.sailex.altoclef.multiversion;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.Optional;

public class EnchantmentVer {

    
    public static int getEnchantmentLevel(ResourceKey<Enchantment> enchantment, ItemStack stack, MinecraftServer server) {
        Registry<Enchantment> enchantmentRegistry =  server.registryAccess().registryOrThrow(Registries.ENCHANTMENT); 
        Optional<Holder.Reference<Enchantment>> entry = enchantmentRegistry.getHolder(enchantment.location());
        return EnchantmentHelper.getItemEnchantmentLevel(entry.get(), stack);
    }
    

}
