// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/FoodComponentWrapper.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion;

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
        return component.nutrition();
    }

    public float getSaturationModifier() {
        return component.saturation();
    }
}
