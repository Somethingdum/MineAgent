package me.sailex.altoclef.multiversion;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.resources.ResourceLocation;

public class RegistriesVer {

    public static <T> T get(DefaultedRegistry<T> registry, ResourceLocation id) {
        
        return registry.getOptional(id).orElse(null);
         
    }

}
