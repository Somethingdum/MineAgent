// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/chains/FoodChain.java
// at commit 1f8e038, in Mojang's names (third_party/altoclef). SMH: what AltoClef's tasks ask of it (needsToEat, its food
// pick, shouldStop) kept as AltoClef has it; the eating itself is the companion's own food chain (survival/FoodChain),
// which takes the body from any task, so this one never eats.
package com.mineagent.engine.alto.chains;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.multiversion.FoodComponentWrapper;
import com.mineagent.engine.alto.multiversion.item.ItemVer;
import com.mineagent.engine.alto.util.helpers.ItemHelper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;
import java.util.Optional;

public class FoodChain {

    private static final FoodChainConfig config = new FoodChainConfig();
    private final AltoClef mod;
    private boolean shouldStop = false;
    private Optional<Item> cachedPerfectFood = Optional.empty();
    private boolean hasFood;

    public FoodChain(AltoClef mod) {
        this.mod = mod;
    }

    /** SMH: once a tick, before the tasks, as AltoClef's getPriority works out its food. */
    public void update() {
        Tuple<Integer, Optional<Item>> calculation = calculateFood(mod);
        cachedPerfectFood = calculation.getB();
        hasFood = calculation.getA() > 0;
    }

    /** SMH: our food chain is eating (it has the body): AltoClef's own chain would be. */
    public boolean isTryingToEat() {
        return mod.companionEating();
    }

    public boolean needsToEat() {
        if (!hasFood() || shouldStop) {
            return false;
        }

        ServerPlayer player = mod.getPlayer();
        assert player != null;
        int foodLevel = player.getFoodData().getFoodLevel();
        float health = player.getHealth();

        if (foodLevel >= 20) {
            // We can't eat.
            return false;
        }

        if (health <= 10) {
            return true;
        }

        // Eat if we're desperate/need to heal ASAP
        if (player.isOnFire() || player.hasEffect(MobEffects.WITHER) || health < config.alwaysEatWhenWitherOrFireAndHealthBelow) {
            return true;
        } else if (foodLevel > config.alwaysEatWhenBelowHunger) {
            if (health < config.alwaysEatWhenBelowHealth) {
                return true;
            }
        } else {
            // We have half hunger
            return true;
        }

        // Eat if we're  units hungry and we have a perfect fit.
        if (foodLevel < config.alwaysEatWhenBelowHungerAndPerfectFit && cachedPerfectFood.isPresent()) {
            int need = 20 - foodLevel;
            Item best = cachedPerfectFood.get();

            int fills = (ItemVer.getFoodComponent(best) != null) ? ItemVer.getFoodComponent(best).getHunger() : -1;
            return fills == need;
        }

        return false;
    }

    private Tuple<Integer, Optional<Item>> calculateFood(AltoClef mod) {
        Item bestFood = null;
        double bestFoodScore = Double.NEGATIVE_INFINITY;
        int foodTotal = 0;
        ServerPlayer player = mod.getPlayer();
        float health = player != null ? player.getHealth() : 20;
        float hunger = player != null ? player.getFoodData().getFoodLevel() : 20;
        float saturation = player != null ? player.getFoodData().getSaturationLevel() : 20;
        // Get best food item + calculate food total
        for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            if (ItemVer.isFood(stack)) {
                // Ignore protected items
                if (!ItemHelper.canThrowAwayStack(mod, stack)) continue;

                // Ignore spider eyes
                if (stack.getItem() == Items.SPIDER_EYE) {
                    continue;
                }

                FoodComponentWrapper food = ItemVer.getFoodComponent(stack.getItem());

                assert food != null;
                float hungerIfEaten = Math.min(hunger + food.getHunger(), 20);
                float saturationIfEaten = Math.min(hungerIfEaten, saturation + food.getSaturationModifier());
                float gainedSaturation = (saturationIfEaten - saturation);
                float gainedHunger = (hungerIfEaten - hunger);
                float hungerNotFilled = 20 - hungerIfEaten;

                float saturationWasted = food.getSaturationModifier() - gainedSaturation;
                float hungerWasted = food.getHunger() - gainedHunger;

                boolean prioritizeSaturation = health < config.prioritizeSaturationWhenBelowHealth;
                float saturationGoodScore = prioritizeSaturation ? gainedSaturation * config.foodPickPrioritizeSaturationSaturationMultiplier : gainedSaturation;
                float saturationLossPenalty = prioritizeSaturation ? 0 : saturationWasted * config.foodPickSaturationWastePenaltyMultiplier;
                float hungerLossPenalty = hungerWasted * config.foodPickHungerWastePenaltyMultiplier;
                float hungerNotFilledPenalty = hungerNotFilled * config.foodPickHungerNotFilledPenaltyMultiplier;

                float score = saturationGoodScore - saturationLossPenalty - hungerLossPenalty - hungerNotFilledPenalty;

                if (stack.getItem() == Items.ROTTEN_FLESH) {
                    score -= config.foodPickRottenFleshPenalty;
                }
                if (score > bestFoodScore) {
                    bestFoodScore = score;
                    bestFood = stack.getItem();
                }

                foodTotal += Objects.requireNonNull(ItemVer.getFoodComponent(stack.getItem())).getHunger() * stack.getCount();
            }
        }

        return new Tuple<>(foodTotal, Optional.ofNullable(bestFood));
    }

    public boolean needsToEatCritical() {
        return false;
    }

    public boolean hasFood() {
        return hasFood;
    }

    public void shouldStop(boolean shouldStopInput) {
        shouldStop = shouldStopInput;
    }

    public boolean isShouldStop() {
        return shouldStop;
    }

    static class FoodChainConfig {
        public int alwaysEatWhenWitherOrFireAndHealthBelow = 6;
        public int alwaysEatWhenBelowHunger = 10;
        public int alwaysEatWhenBelowHealth = 14;
        public int alwaysEatWhenBelowHungerAndPerfectFit = 20 - 5;
        public int prioritizeSaturationWhenBelowHealth = 8;
        public float foodPickPrioritizeSaturationSaturationMultiplier = 8;
        public float foodPickSaturationWastePenaltyMultiplier = 1;
        public float foodPickHungerWastePenaltyMultiplier = 2;
        public float foodPickHungerNotFilledPenaltyMultiplier = 1;
        public float foodPickRottenFleshPenalty = 100;
        public float runDontEatMaxHealth = 3;
        public int runDontEatMaxHunger = 3;
        public int canTankHitsAndEatArmor = 15;
        public int canTankHitsAndEatMaxHunger = 3;
    }
}
