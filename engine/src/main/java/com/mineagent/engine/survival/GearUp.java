package com.mineagent.engine.survival;

import com.mineagent.engine.task.TaskContext;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Gearing up (SMH plan 69, MA3.6): the best armour piece it carries into each slot by armour value (toughness to
 * break a tie), and a shield into the off hand. Done when a fight begins and once each dusk. Only swaps, so the
 * item counts and every enchantment and durability stay as they were. It never takes off a cursed piece, an elytra,
 * or a totem from the off hand.
 */
public final class GearUp {
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private GearUp() {}

    /** Inventory index of an armour slot (the player inventory's layout: 36 feet .. 39 head, 40 off hand). */
    private static int index(EquipmentSlot slot) {
        return switch (slot) {
            case FEET -> 36;
            case LEGS -> 37;
            case CHEST -> 38;
            case HEAD -> 39;
            default -> 40;
        };
    }

    /** Equip what is better than what it wears; whether anything changed. */
    public static boolean run(ServerPlayer sp) {
        var inv = sp.getInventory();
        boolean changed = false;
        for (EquipmentSlot slot : ARMOUR) {
            int worn = index(slot);
            ItemStack current = inv.getItem(worn);
            if (!current.isEmpty() && (!(current.getItem() instanceof ArmorItem)
                    || EnchantmentHelper.has(current, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))) {
                continue; // an elytra or a pumpkin is on for a reason; a cursed piece does not come off
            }
            int best = -1;
            double bestValue = value(current);
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inv.getItem(i);
                if (!(stack.getItem() instanceof ArmorItem) || sp.getEquipmentSlotForItem(stack) != slot) continue;
                double v = value(stack);
                if (v > bestValue) {
                    bestValue = v;
                    best = i;
                }
            }
            if (best >= 0) {
                ItemStack better = inv.getItem(best);
                inv.setItem(worn, better);
                inv.setItem(best, current);
                changed = true;
            }
        }
        ItemStack off = inv.getItem(40);
        if (!(off.getItem() instanceof ShieldItem) && !off.is(Items.TOTEM_OF_UNDYING)) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.getItem() instanceof ShieldItem) {
                    inv.setItem(40, stack);
                    inv.setItem(i, off);
                    changed = true;
                    break;
                }
            }
        }
        if (changed) TaskContext.syncInventory(sp);
        return changed;
    }

    /** Armour value with toughness as the tie-break, and a little for durability left. */
    static double value(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armour)) return -1.0;
        double wear = stack.isDamageableItem() && stack.getMaxDamage() > 0
                ? 1.0 - (double) stack.getDamageValue() / stack.getMaxDamage() : 1.0;
        // A piece about to break is worth less than a sound one a tier down.
        return armour.getDefense() + armour.getToughness() * 0.1 + (wear < 0.1 ? -1.5 : 0.0);
    }
}
