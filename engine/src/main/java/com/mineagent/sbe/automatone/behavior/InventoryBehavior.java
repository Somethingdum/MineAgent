// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/behavior/InventoryBehavior.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mineagent.sbe.automatone.behavior;

import com.mineagent.sbe.altoclef.multiversion.PlayerInventoryVer;
import com.mineagent.sbe.automatone.Baritone;
import com.mineagent.sbe.automatone.utils.ToolSet;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Predicate;

public final class InventoryBehavior extends Behavior {

    public InventoryBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTickServer() {
        if (!baritone.settings().allowInventory.get() || baritone.getExtraBaritoneSettings().isInteractionPaused()) { // SMH: AltoClef 0002
            return;
        }
        Player player = ctx.entity();
        if (player.inventoryMenu != player.containerMenu) {
            // we have a crafting table or a chest or something open
            return;
        }
        if (firstValidThrowaway(player.getInventory()) >= 9) { // aka there are none on the hotbar, but there are some in main inventory
            swapWithHotBar(firstValidThrowaway(player.getInventory()), 8, player.getInventory());
        }
        int pick = bestToolAgainst(Blocks.STONE, ItemTags.PICKAXES);
        if (pick >= 9) {
            swapWithHotBar(pick, 0, player.getInventory());
        }
    }

    public void attemptToPutOnHotbar(int inMainInvy, Predicate<Integer> disallowedHotbar, Inventory inventory) {
        OptionalInt destination = getTempHotbarSlot(disallowedHotbar);
        if (destination.isPresent()) {
            swapWithHotBar(inMainInvy, destination.getAsInt(), inventory);
        }
    }

    public OptionalInt getTempHotbarSlot(Predicate<Integer> disallowedHotbar) {
        Inventory inventory = ctx.inventory();
        if (inventory == null) return OptionalInt.empty();

        // we're using 0 and 8 for pickaxe and throwaway
        ArrayList<Integer> candidates = new ArrayList<>();
        for (int i = 1; i < 8; i++) {
            if (PlayerInventoryVer.getMainInventory(inventory).get(i).isEmpty() && !disallowedHotbar.test(i)) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) {
            for (int i = 1; i < 8; i++) {
                if (!disallowedHotbar.test(i)) {
                    candidates.add(i);
                }
            }
        }

        if (candidates.isEmpty()) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(candidates.get(new Random().nextInt(candidates.size())));
    }

    private void swapWithHotBar(int inInventory, int inHotbar, Inventory inventory) {
        if (baritone.getExtraBaritoneSettings().isInteractionPaused()) { // SMH: AltoClef 0002
            return;
        }
        ItemStack h = inventory.getItem(inHotbar);
        inventory.setItem(inHotbar, inventory.getItem(inInventory));
        inventory.setItem(inInventory, h);
    }

    private int firstValidThrowaway(Inventory inventory) { // TODO offhand idk
        NonNullList<ItemStack> invy = PlayerInventoryVer.getMainInventory(inventory);
        for (int i = 0; i < invy.size(); i++) {
            if (baritone.settings().acceptableThrowawayItems.get().contains(invy.get(i).getItem())
                    && !baritone.getExtraBaritoneSettings().isItemProtected(invy.get(i).getItem())) { // SMH: AltoClef 0002
                return i;
            }
        }
        return -1;
    }

    private int bestToolAgainst(Block against, TagKey<Item> tagKey) {
        NonNullList<ItemStack> invy = PlayerInventoryVer.getMainInventory(ctx.inventory());
        int bestInd = -1;
        double bestSpeed = -1;
        for (int i = 0; i < invy.size(); i++) {
            ItemStack stack = invy.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (baritone.settings().itemSaver.get() && stack.getDamageValue() >= stack.getMaxDamage() && stack.getMaxDamage() > 1) {
                continue;
            }
            if (stack.is(tagKey)) {
                double speed = ToolSet.calculateSpeedVsBlock(stack, against.defaultBlockState(), ctx.entity()); // takes into account enchants
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestInd = i;
                }
            }
        }
        return bestInd;
    }

    public boolean hasGenericThrowaway() {
        return throwaway(false,
                stack -> baritone.settings().acceptableThrowawayItems.get().contains(stack.getItem())
                        && !baritone.getExtraBaritoneSettings().isItemProtected(stack.getItem())); // SMH: AltoClef 0002
    }

    public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {
        Player player = ctx.entity();
        if (baritone.getExtraBaritoneSettings().shouldAvoidPlacingAt(x, y, z)) { // SMH: AltoClef 0002
            return false;
        }

        BlockState maybe = baritone.getBuilderProcess().placeAt(x, y, z, baritone.bsi.get0(x, y, z));
        if (maybe != null && throwaway(select, stack -> stack.getItem() instanceof BlockItem && maybe.equals(((BlockItem) stack.getItem()).getBlock().getStateForPlacement(new BlockPlaceContext(new UseOnContext(ctx.world(), player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(new Vec3(player.getX(), player.getY(), player.getZ()), Direction.UP, ctx.feetPos(), false)) {}))))) {
            return true; // gotem
        }
        if (maybe != null && throwaway(select, stack -> stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock().equals(maybe.getBlock()))) {
            return true;
        }
        return throwaway(select,
                stack -> baritone.settings().acceptableThrowawayItems.get().contains(stack.getItem())
                        && !baritone.getExtraBaritoneSettings().isItemProtected(stack.getItem())); // SMH: AltoClef 0002
    }

    public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {
        if (baritone.getExtraBaritoneSettings().isInteractionPaused()) { // SMH: AltoClef 0002
            return false;
        }
        Player p = ctx.entity();

        NonNullList<ItemStack> inv = PlayerInventoryVer.getMainInventory(p.getInventory());
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.get(i);
            // this usage of settings() is okay because it's only called once during pathing
            // (while creating the CalculationContext at the very beginning)
            // and then it's called during execution
            // since this function is never called during cost calculation, we don't need to migrate
            // acceptableThrowawayItems to the CalculationContext
            if (desired.test(item)) {
                if (select) {
                    PlayerInventoryVer.setSelectedSlot(p.getInventory(), i);
                }
                return true;
            }
        }
        if (desired.test(PlayerInventoryVer.getOffHandStack(p.getInventory()).get(0))) {
            // main hand takes precedence over off hand
            // that means that if we have block A selected in main hand and block B in off hand, right clicking places block B
            // we've already checked above ^ and the main hand can't possible have an acceptablethrowawayitem
            // so we need to select in the main hand something that doesn't right click
            // so not a shovel, not a hoe, not a block, etc
            for (int i = 0; i < 9; i++) {
                ItemStack item = inv.get(i);
                if (item.isEmpty() || item.is(ItemTags.PICKAXES)) {
                    if (select) {
                        PlayerInventoryVer.setSelectedSlot(p.getInventory(), i);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public static int getSlotWithStack(Inventory inv, TagKey<Item> tag) {
        NonNullList<ItemStack> main = PlayerInventoryVer.getMainInventory(inv);
        for(int i = 0; i < main.size(); ++i) {
            if (!main.get(i).isEmpty() && main.get(i).is(tag)) {
                return i;
            }
        }

        return -1;
    }
}
