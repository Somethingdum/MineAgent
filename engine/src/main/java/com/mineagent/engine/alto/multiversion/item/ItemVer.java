// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/item/ItemVer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion.item;

import com.mineagent.engine.alto.multiversion.FoodComponentWrapper;
import com.mineagent.engine.alto.multiversion.Pattern;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public class ItemVer {

    public static FoodComponentWrapper getFoodComponent(Item item) {
        return FoodComponentWrapper.of(item.components().get(net.minecraft.core.component.DataComponents.FOOD));
    }

    public static boolean isFood(ItemStack stack) {
        return isFood(stack.getItem());
    }

    public static boolean hasCustomName(ItemStack stack) {
        return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
    }

    public static boolean isFood(Item item) {
        return item.components().has(net.minecraft.core.component.DataComponents.FOOD);
    }

    @Pattern
    private static boolean isSuitableFor(Item item, BlockState state) {
        return item.getDefaultInstance().isCorrectToolForDrops(state);
    }

    // the fact that this works is insane...
    @Pattern
    private static Item RAW_GOLD() {
        return Items.RAW_GOLD;
    }

    @Pattern
    private static Item RAW_IRON() {
        return Items.RAW_IRON;
    }


}
