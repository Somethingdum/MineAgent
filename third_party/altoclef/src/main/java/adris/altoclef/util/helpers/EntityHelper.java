package adris.altoclef.util.helpers;

import adris.altoclef.AltoClef;
import adris.altoclef.multiversion.DamageSourceWrapper;
import adris.altoclef.multiversion.MethodWrapper;
import net.minecraft.entity.mob.*;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Helper functions to interpret entity state
 */
public class EntityHelper {
    public static final double ENTITY_GRAVITY = 0.08; // per second

    public static boolean isAngryAtPlayer(AltoClef mod, Entity mob) {
        boolean hostile = isProbablyHostileToPlayer(mod, mob);
        if (mob instanceof LivingEntity entity) {
            return hostile && entity.hasLineOfSight(mod.getPlayer());
        }
        return hostile;
    }

    public static boolean isProbablyHostileToPlayer(AltoClef mod, Entity entity) {
        if (entity instanceof Mob mob) {
            if (mob instanceof Slime slime) {
                return slime.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0;
            }
            if (mob instanceof Piglin piglin) {
                return piglin.isAggressive() && !isTradingPiglin(mob) && piglin.isAdult();
            }
            if (mob instanceof EnderMan enderman) {
                return enderman.isCreepy();
            }
            if (mob instanceof ZombifiedPiglin zombifiedPiglin) {
                return zombifiedPiglin.isAggressive();
            }

            return mob.isAggressive() || mob instanceof Monster;
        }

        return false;
    }

    public static boolean isTradingPiglin(Entity entity) {
        if (entity instanceof Piglin pig) {
            if (pig.getHandSlots() != null) {
                for (ItemStack stack : pig.getHandSlots()) {
                    if (stack.getItem().equals(Items.GOLD_INGOT)) {
                        // We're trading with this one, ignore it.
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Calculate the resulting damage dealt to a player as a result of some damage.
     * If this player were to receive this damage, the player's health will be subtracted by the resulting value.
     */
    public static double calculateResultingPlayerDamage(Player player, DamageSource src, double damageAmount) {
        // Copied logic from `PlayerEntity.applyDamage`
        DamageSourceWrapper source = DamageSourceWrapper.of(src);

        if (player.isInvulnerableTo(src))
            return 0;

        // Armor Base
        if (!source.bypassesArmor()) {
            damageAmount = MethodWrapper.getDamageLeft(player, damageAmount,src,player.getArmorValue(),player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }

        // Enchantments & Potions
        if (!source.bypassesShield()) {
            float k;
            if (player.hasEffect(MobEffects.DAMAGE_RESISTANCE) && source.isOutOfWorld()) {
                //noinspection ConstantConditions
                k = (player.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
                float j = 25 - k;
                double f = damageAmount * (double) j;
                double g = damageAmount;
                damageAmount = Math.max(f / 25.0F, 0.0F);
            }

            if (damageAmount <= 0.0) {
                damageAmount = 0.0;
            } else {
                //#if MC >= 12100
                k = EnchantmentHelper.getDamageProtection(null, player, src);
                //#else
                //$$ k = EnchantmentHelper.getProtectionAmount(player.getArmorItems(), src);
                //#endif
                if (k > 0) {
                    damageAmount = CombatRules.getDamageAfterMagicAbsorb((float) damageAmount, (float) k);
                }
            }
        }

        // Absorption
        damageAmount = Math.max(damageAmount - player.getAbsorptionAmount(), 0.0F);
        return damageAmount;
    }
}
