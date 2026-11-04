package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.tasks.container.SmeltInSmokerTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.SmeltTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.SmokerSlot;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.entity.passive.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class CollectMeatTask extends Task {
    public static final CookableFoodTarget[] COOKABLE_FOODS = new CookableFoodTarget[]{
            new CookableFoodTarget("beef", Cow.class),
            new CookableFoodTarget("porkchop", Pig.class),
            new CookableFoodTarget("chicken", Chicken.class),
            new CookableFoodTarget("mutton", Sheep.class),
            new CookableFoodTarget("rabbit", Rabbit.class)
    };
    private final double unitsNeeded;
    private final TimerGame checkNewOptionsTimer = new TimerGame(10);
    private SmeltInSmokerTask smeltTask = null;
    private Task currentResourceTask = null;

    public CollectMeatTask(double unitsNeeded) {
        this.unitsNeeded = unitsNeeded;
    }

    private static double getFoodPotential(ItemStack food) {
        if (food == null) return 0;
        int count = food.getCount();
        if (count <= 0) return 0;
        for (CookableFoodTarget cookable : COOKABLE_FOODS) {
            if (food.getItem() == cookable.getRaw()) {
                assert ItemVer.getFoodComponent(cookable.getCooked()) != null;
                return count * ItemVer.getFoodComponent(cookable.getCooked()).getHunger();
            }
        }
        return 0;
    }

    private static double calculateFoodPotential(AltoClef mod) {
        double potentialFood = 0;
        for (ItemStack food : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            potentialFood += getFoodPotential(food);
        }
        // Check smelting
        AbstractContainerMenu screen = mod.getPlayer().containerMenu;
        if (screen instanceof SmokerMenu) {
            potentialFood += getFoodPotential(StorageHelper.getItemStackInSlot(SmokerSlot.INPUT_SLOT_MATERIALS));
            potentialFood += getFoodPotential(StorageHelper.getItemStackInSlot(SmokerSlot.OUTPUT_SLOT));
        }
        return potentialFood;
    }

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        CollectFoodTask.blackListChickenJockeys(mod);
        // If we were previously smelting, keep on smelting.
        if (smeltTask != null && smeltTask.isActive() && !smeltTask.isFinished()) {
            setDebugState("Cooking...");
            return smeltTask;
        } else {
            smeltTask = null;
        }
        if (checkNewOptionsTimer.elapsed()) {
            // Try a new resource task
            checkNewOptionsTimer.reset();
            currentResourceTask = null;
        }
        if (currentResourceTask != null && currentResourceTask.isActive() && !currentResourceTask.isFinished() && !currentResourceTask.thisOrChildAreTimedOut()) {
            return currentResourceTask;
        }
        // Calculate potential
        double potentialFood = calculateFoodPotential(mod);
        if (potentialFood >= unitsNeeded) {
            // Convert our raw foods
            // PLAN:
            // - If we have raw foods, smelt all of them
            // Convert raw foods -> cooked foods
            for (CookableFoodTarget cookable : COOKABLE_FOODS) {
                int rawCount = mod.getItemStorage().getItemCount(cookable.getRaw());
                if (rawCount > 0) {
                    //Debug.logMessage("STARTING COOK OF " + cookable.getRaw().getTranslationKey());
                    int toSmelt = rawCount + mod.getItemStorage().getItemCount(cookable.getCooked());
                    smeltTask = new SmeltInSmokerTask(new SmeltTarget(new ItemTarget(cookable.cookedFood, toSmelt), new ItemTarget(cookable.rawFood, rawCount)));
                    smeltTask.ignoreMaterials();
                    return smeltTask;
                }
            }
        } else {
            // Pick up raw/cooked foods on ground
            for (CookableFoodTarget cookable : COOKABLE_FOODS) {
                Task t = this.pickupTaskOrNull(mod, cookable.getRaw(), 20);
                if (t == null) t = this.pickupTaskOrNull(mod, cookable.getCooked(), 40);
                if (t != null) {
                    setDebugState("Picking up Cookable food");
                    currentResourceTask = t;
                    return currentResourceTask;
                }
            }
            // Cooked foods
            double bestScore = 0;
            Entity bestEntity = null;
            Item bestRawFood = null;
            for (CookableFoodTarget cookable : COOKABLE_FOODS) {
                if (!mod.getEntityTracker().entityFound(cookable.mobToKill)) continue;
                Optional<Entity> nearest = mod.getEntityTracker().getClosestEntity(mod.getPlayer().position(), cookable.mobToKill);
                if (nearest.isEmpty()) continue; // ?? This crashed once?
                int hungerPerformance = cookable.getCookedUnits();
                double sqDistance = nearest.get().distanceToSqr(mod.getPlayer());
                double score = (double) 100 * hungerPerformance / (sqDistance);
                if (score > bestScore) {
                    bestScore = score;
                    bestEntity = nearest.get();
                    bestRawFood = cookable.getRaw();
                }
            }
            if (bestEntity != null) {
                setDebugState("Killing " + bestEntity.getType().getDescriptionId());
                Predicate<Entity> notBaby = entity -> entity instanceof LivingEntity livingEntity && !livingEntity.isBaby();
                currentResourceTask = killTaskOrNull(bestEntity, notBaby, bestRawFood);
                return currentResourceTask;
            }
        }
        for (Item raw : ItemHelper.RAW_FOODS) {
            if (mod.getItemStorage().hasItem(raw)) {
                Optional<Item> cooked = ItemHelper.getCookedFood(raw);
                if (cooked.isPresent()) {
                    int targetCount = mod.getItemStorage().getItemCount(cooked.get()) + mod.getItemStorage().getItemCount(raw);
                    smeltTask = new SmeltInSmokerTask(new SmeltTarget(new ItemTarget(cooked.get(), targetCount), new ItemTarget(raw, targetCount)));
                    return smeltTask;
                }
            }
        }
        // Look for food.
        setDebugState("Searching...");
        return new TimeoutWanderTask();
    }

    private Task killTaskOrNull(Entity entity, Predicate<Entity> entityPredicate, Item itemToGrab) {
        return new KillAndLootTask(entity.getClass(), entityPredicate, new ItemTarget(itemToGrab, 1));
    }

    private Task pickupTaskOrNull(AltoClef mod, Item itemToGrab, double maxRange) {
        Optional<ItemEntity> nearestDrop = Optional.empty();
        if (mod.getEntityTracker().itemDropped(itemToGrab)) {
            nearestDrop = mod.getEntityTracker().getClosestItemDrop(mod.getPlayer().position(), itemToGrab);
        }
        if (nearestDrop.isPresent()) {
            if (nearestDrop.get().closerThan(mod.getPlayer(), maxRange)) {
                return new PickupDroppedItemTask(new ItemTarget(itemToGrab), true);
            }
            //return new GetToBlockTask(nearestDrop.getBlockPos(), false);
        }
        return null;
    }

    private Task pickupTaskOrNull(AltoClef mod, Item itemToGrab) {
        return pickupTaskOrNull(mod, itemToGrab, Double.POSITIVE_INFINITY);
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    public boolean isFinished() {
        return StorageHelper.calculateInventoryFoodScore() >= unitsNeeded && smeltTask == null;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof CollectMeatTask task) {
            return task.unitsNeeded == unitsNeeded;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Collect " + unitsNeeded + " units of meat.";
    }

    public static class CookableFoodTarget {
        public String rawFood;
        public String cookedFood;
        public Class<?> mobToKill;

        public CookableFoodTarget(String rawFood, String cookedFood, Class<?> mobToKill) {
            this.rawFood = rawFood;
            this.cookedFood = cookedFood;
            this.mobToKill = mobToKill;
        }

        public CookableFoodTarget(String rawFood, Class<?> mobToKill) {
            this(rawFood, "cooked_" + rawFood, mobToKill);
        }

        public Item getRaw() {
            return Objects.requireNonNull(TaskCatalogue.getItemMatches(rawFood))[0];
        }

        public Item getCooked() {
            return Objects.requireNonNull(TaskCatalogue.getItemMatches(cookedFood))[0];
        }

        public int getCookedUnits() {
            assert ItemVer.getFoodComponent(getCooked()) != null;
            return ItemVer.getFoodComponent(getCooked()).getHunger();
        }
    }
}
