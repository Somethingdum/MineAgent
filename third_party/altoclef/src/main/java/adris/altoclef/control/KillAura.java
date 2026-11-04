package adris.altoclef.control;

import adris.altoclef.AltoClef;
import adris.altoclef.multiversion.versionedfields.Entities;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StlHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import baritone.api.utils.input.Input;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

/**
 * Controls and applies killaura
 */
public class KillAura {
    // Smart aura data
    private final List<Entity> targets = new ArrayList<>();
    boolean shielding = false;
    private double forceFieldRange = Double.POSITIVE_INFINITY;
    private Entity forceHit = null;
    public boolean attackedLastTick = false;

    public static void equipWeapon(AltoClef mod) {
        List<ItemStack> invStacks = mod.getItemStorage().getItemStacksPlayerInventory(true);
        if (!invStacks.isEmpty()) {
            float handDamage = Float.NEGATIVE_INFINITY;
            for (ItemStack invStack : invStacks) {
                if (invStack.getItem() instanceof SwordItem item) {
                    float itemDamage = item.getTier().getAttackDamageBonus();
                    Item handItem = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot()).getItem();
                    if (handItem instanceof SwordItem handToolItem) {
                        handDamage = handToolItem.getTier().getAttackDamageBonus();
                    }
                    if (itemDamage > handDamage) {
                        mod.getSlotHandler().forceEquipItem(item);
                    } else {
                        mod.getSlotHandler().forceEquipItem(handItem);
                    }
                }
            }
        }
    }

    public void tickStart() {
        targets.clear();
        forceHit = null;
        attackedLastTick = false;
    }

    public void applyAura(Entity entity) {
        targets.add(entity);
        // Always hit ghast balls.
        if (entity instanceof LargeFireball) forceHit = entity;
    }

    public void setRange(double range) {
        forceFieldRange = range;
    }

    public void tickEnd(AltoClef mod) {
        Optional<Entity> entities = targets.stream().min(StlHelper.compareValues(entity -> entity.distanceToSqr(mod.getPlayer())));
        if (entities.isPresent() &&
                !mod.getEntityTracker().entityFound(ThrownPotion.class) &&
                (Double.isInfinite(forceFieldRange) || entities.get().distanceToSqr(mod.getPlayer()) < forceFieldRange * forceFieldRange ||
                        entities.get().distanceToSqr(mod.getPlayer()) < 40) &&
                !mod.getMLGBucketChain().isFalling(mod) && mod.getMLGBucketChain().doneMLG() &&
                !mod.getMLGBucketChain().isChorusFruiting()) {
            PlayerSlot offhandSlot = PlayerSlot.OFFHAND_SLOT;
            Item offhandItem = StorageHelper.getItemStackInSlot(offhandSlot).getItem();
            if (entities.get().getClass() != Creeper.class && entities.get().getClass() != Hoglin.class &&
                    entities.get().getClass() != Zoglin.class && entities.get().getClass() != Entities.WARDEN &&
                    entities.get().getClass() != WitherBoss.class
                    && (mod.getItemStorage().hasItem(Items.SHIELD) || mod.getItemStorage().hasItemInOffhand(Items.SHIELD))
                    && !mod.getPlayer().getCooldowns().isOnCooldown(offhandItem)
                    && mod.getClientBaritone().getPathingBehavior().isSafeToCancel()) {
                LookHelper.lookAt(mod, entities.get().getEyePosition());
                ItemStack shieldSlot = StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT);
                if (shieldSlot.getItem() != Items.SHIELD) {
                    mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                } else if (!WorldHelper.isSurroundedByHostiles()) {
                    startShielding(mod);
                }
            }
            performDelayedAttack(mod);
        } else {
            stopShielding(mod);
        }
        // Run force field on map
        switch (mod.getModSettings().getForceFieldStrategy()) {
            case FASTEST:
                performFastestAttack(mod);
                break;
            case SMART:
                // Attack force mobs ALWAYS. (currently used only for fireballs)
                if (forceHit != null) {
                    attack(mod, forceHit, true);
                    break;
                }

                if (!mod.getFoodChain().needsToEat() && !mod.getMLGBucketChain().isFalling(mod) &&
                        mod.getMLGBucketChain().doneMLG() && !mod.getMLGBucketChain().isChorusFruiting()) {
                    performDelayedAttack(mod);
                }
                break;
            case DELAY:
                performDelayedAttack(mod);
                break;
            case OFF:
                break;
        }
    }

    private void performDelayedAttack(AltoClef mod) {
        if (!mod.getFoodChain().needsToEat() && !mod.getMLGBucketChain().isFalling(mod) &&
                mod.getMLGBucketChain().doneMLG() && !mod.getMLGBucketChain().isChorusFruiting()) {
            if (forceHit != null) {
                attack(mod, forceHit, true);
            }
            // wait for the attack delay
            if (targets.isEmpty()) {
                return;
            }

            Optional<Entity> toHit = targets.stream().min(StlHelper.compareValues(entity -> entity.distanceToSqr(mod.getPlayer())));

            if (mod.getPlayer() == null || mod.getPlayer().getAttackStrengthScale(0) < 1) {
                return;
            }

            toHit.ifPresent(entity -> attack(mod, entity, true));
        }
    }

    private void performFastestAttack(AltoClef mod) {
        if (!mod.getFoodChain().needsToEat() && !mod.getMLGBucketChain().isFalling(mod) &&
                mod.getMLGBucketChain().doneMLG() && !mod.getMLGBucketChain().isChorusFruiting()) {
            // Just attack whenever you can
            for (Entity entity : targets) {
                attack(mod, entity);
            }
        }
    }

    private void attack(AltoClef mod, Entity entity) {
        attack(mod, entity, false);
    }

    private void attack(AltoClef mod, Entity entity, boolean equipSword) {
        if (entity == null) return;
        if (!(entity instanceof LargeFireball)) {
            double xAim = entity.getX();
            double yAim = entity.getY() + (entity.getBbHeight() / 1.4);
            double zAim = entity.getZ();
            LookHelper.lookAt(mod, new Vec3(xAim, yAim, zAim));
        }
        if (Double.isInfinite(forceFieldRange) || entity.distanceToSqr(mod.getPlayer()) < forceFieldRange * forceFieldRange ||
                entity.distanceToSqr(mod.getPlayer()) < 40) {
            if (entity instanceof LargeFireball) {
                mod.getControllerExtras().attack(entity);
            }
            boolean canAttack;
            if (equipSword) {
                equipWeapon(mod);
                canAttack = true;
            } else {
                // Equip non-tool
                canAttack = mod.getSlotHandler().forceDeequipHitTool();
            }
            if (canAttack) {
                if (mod.getPlayer().onGround() || mod.getPlayer().getDeltaMovement().y() < 0 || mod.getPlayer().isInWater()) {
                    attackedLastTick = true;
                    mod.getControllerExtras().attack(entity);
                }
            }
        }
    }

    public void startShielding(AltoClef mod) {
        shielding = true;
        mod.getClientBaritone().getPathingBehavior().requestPause();
        mod.getExtraBaritoneSettings().setInteractionPaused(true);
        if (!mod.getPlayer().isBlocking()) {
            ItemStack handItem = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot());
            if (ItemVer.isFood(handItem)) {
                List<ItemStack> spaceSlots = mod.getItemStorage().getItemStacksPlayerInventory(false);
                if (!spaceSlots.isEmpty()) {
                    for (ItemStack spaceSlot : spaceSlots) {
                        if (spaceSlot.isEmpty()) {
                            mod.getSlotHandler().clickSlot(PlayerSlot.getEquipSlot(), 0, ClickType.QUICK_MOVE);
                            return;
                        }
                    }
                }
                Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
                garbage.ifPresent(slot -> mod.getSlotHandler().forceEquipItem(StorageHelper.getItemStackInSlot(slot).getItem()));
            }
        }
        mod.getInputControls().hold(Input.SNEAK);
        mod.getInputControls().hold(Input.CLICK_RIGHT);
    }

    public void stopShielding(AltoClef mod) {
        if (shielding) {
            ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
            if (ItemVer.isFood(cursor)) {
                Optional<Slot> toMoveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false).or(() -> StorageHelper.getGarbageSlot(mod));
                if (toMoveTo.isPresent()) {
                    Slot garbageSlot = toMoveTo.get();
                    mod.getSlotHandler().clickSlot(garbageSlot, 0, ClickType.PICKUP);
                }
            }
            mod.getInputControls().release(Input.SNEAK);
            mod.getInputControls().release(Input.CLICK_RIGHT);
            mod.getInputControls().release(Input.JUMP);
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
            shielding = false;
        }
    }

    public boolean isShielding() {
        return shielding;
    }

    public enum Strategy {
        OFF,
        FASTEST,
        DELAY,
        SMART
    }
}