// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/EnchantmentVer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion;

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
