// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/process/FishingProcess.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.process;

import com.mineagent.sbe.altoclef.multiversion.EnchantmentVer;
import com.mineagent.sbe.altoclef.multiversion.PlayerInventoryVer;
import com.mineagent.sbe.automatone.Baritone;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalBlock;
import com.mineagent.sbe.automatone.api.process.IBaritoneProcess;
import com.mineagent.sbe.automatone.api.process.PathingCommand;
import com.mineagent.sbe.automatone.api.process.PathingCommandType;
import com.mineagent.sbe.automatone.api.utils.Rotation;
import com.mineagent.sbe.automatone.api.utils.RotationUtils;
import com.mineagent.sbe.automatone.pathing.movement.MovementHelper;
import com.mineagent.sbe.automatone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.stream.StreamSupport;

public final class FishingProcess extends BaritoneProcessHelper implements IBaritoneProcess {

    private enum State {
        IDLE,
        WALKING_TO_WATER,
        PREPARING_TO_CAST,
        CASTING,
        WAITING_FOR_BITE,
        REELING_IN,
        WAITING_FOR_ITEMS,
        RECAST_DELAY;
    }

    private boolean active = false;
    private State currentState = State.IDLE;

    private BlockPos fishingSpot = null;
    private FishingHook bobber = null;
    private int timeoutTicks = 0;

    public FishingProcess(Baritone baritone) {
        super(baritone);
    }

    public void fish() {
        this.active = true;
        this.currentState = State.IDLE;
        this.fishingSpot = null;
        this.bobber = null;
        this.timeoutTicks = 0;
        logDirect("Fishing process started.");
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void onLostControl() {
        active = false;
        bobber = null;
    }

    @Override
    public String displayName0() {
        return "Fishing: " + currentState.toString();
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (findFishingRodSlot() == -1) {
            logDirect("No fishing rod in hotbar!");
            onLostControl();
            return null;
        }

        if (calcFailed) {
            logDirect("Failed to path to water, stopping.");
            onLostControl();
            return null;
        }

        switch (currentState) {
            case IDLE:
                return handleIdleState();

            case WALKING_TO_WATER:
                return handleWalkingState();

            case PREPARING_TO_CAST:
                handlePreparingToCastState();
                break;

            case CASTING:
                handleCastingState();
                break;

            case WAITING_FOR_BITE:
                handleWaitingForBiteState();
                break;

            case REELING_IN:
                handleReelingInState();
                break;

            case WAITING_FOR_ITEMS:
            case RECAST_DELAY:
                handleDelayStates();
                break;
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand handleIdleState() {
        Optional<BlockPos> spot = findWaterSpot();
        if (spot.isPresent()) {
            fishingSpot = spot.get().above();
            logDirect("Found a fishing spot. Walking to " + fishingSpot);
            currentState = State.WALKING_TO_WATER;
            return new PathingCommand(new GoalBlock(fishingSpot), PathingCommandType.SET_GOAL_AND_PATH);
        } else {
            logDirect("No suitable water spot found nearby.");
            onLostControl();
            return null;
        }
    }

    private PathingCommand handleWalkingState() {
        Goal goal = new GoalBlock(fishingSpot);
        if (goal.isInGoal(ctx.feetPos())) {
            logDirect("Arrived at fishing spot.");
            currentState = State.PREPARING_TO_CAST;
            timeoutTicks = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
    }

    private void handlePreparingToCastState() {
        BlockPos waterBlock = findAdjacentWater(fishingSpot.below());
        if (waterBlock == null) {
            onLostControl();
            return;
        }

        Rotation rotation = RotationUtils.calcRotationFromVec3d(ctx.headPos(), Vec3.atCenterOf(waterBlock));
        baritone.getLookBehavior().updateTarget(rotation, true);

        if (ctx.entityRotations().isReallyCloseTo(rotation)) {
            currentState = State.CASTING;
        }
    }

    private void handleCastingState() {
        equipFishingRod();
        useFishingRod(ctx.world(), ctx.entity(), InteractionHand.MAIN_HAND);
        logDirect("Casting line...");
        currentState = State.WAITING_FOR_BITE;
        timeoutTicks = 0;
    }

    private void handleWaitingForBiteState() {
        if (this.bobber == null || !this.bobber.isAlive() || timeoutTicks<30) {
            this.bobber = findOurBobber();
            if (this.bobber == null || timeoutTicks<30) {
                timeoutTicks++;
                if (timeoutTicks > 50) {
                    logDirect("Bobber not found, recasting.");
                    currentState = State.RECAST_DELAY;
                }
                return;
            }
        }


        if (this.bobber.getDeltaMovement().y < -0.04) {
            logDirect("Fish on the hook!");
            currentState = State.REELING_IN;
            return;
        }

        timeoutTicks++;
        if (timeoutTicks > 1200) {
            logDirect("No bite for a long time, recasting.");
            currentState = State.RECAST_DELAY;
        }
    }

    private void handleReelingInState() {
        equipFishingRod();
        useFishingRod(ctx.world(), ctx.entity(), InteractionHand.MAIN_HAND);
        logDirect("Reeling in!");
        this.bobber = null;
        currentState = State.WAITING_FOR_ITEMS;
        timeoutTicks = 20;
    }

    private void handleDelayStates() {
        timeoutTicks--;
        if (timeoutTicks <= 0) {
            if (currentState == State.WAITING_FOR_ITEMS) {
                currentState = State.RECAST_DELAY;
                timeoutTicks = 10;
            } else {
                currentState = State.PREPARING_TO_CAST;
            }
        }
    }


    private Optional<BlockPos> findWaterSpot() {
        BlockPos center = ctx.feetPos();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -3, -8), center.offset(8, 3, 8))) {
            if (ctx.world().getBlockState(pos).is(Blocks.WATER)) {
                if (ctx.world().getBlockState(pos.above()).isAir() && ctx.world().getBlockState(pos.above(2)).isAir()) {
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos standPos = pos.relative(dir);
                        if (MovementHelper.canWalkOn(ctx, standPos) && ctx.world().getBlockState(standPos.above()).isAir() && ctx.world().getBlockState(standPos.above(2)).isAir()) {
                            return Optional.of(standPos.immutable());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private BlockPos findAdjacentWater(BlockPos position) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (ctx.world().getBlockState(position.relative(dir)).is(Blocks.WATER)) {
                return position.relative(dir);
            }
        }
        return null;
    }

    private int findFishingRodSlot() {
        Inventory inv = ctx.inventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).getItem() instanceof FishingRodItem) {
                return i;
            }
        }
        return -1;
    }

    private void equipFishingRod() {
        int slot = findFishingRodSlot();
        if (slot != -1) {
            PlayerInventoryVer.setSelectedSlot(ctx.inventory(), slot);
        }
    }

    @Nullable
    private FishingHook findOurBobber() {
        return (FishingHook) StreamSupport.stream(ctx.world().getAllEntities().spliterator(), false)
                .filter(e -> e instanceof FishingHook)
                .filter(e -> ((FishingHook) e).getPlayerOwner() == ctx.entity())
                .findFirst()
                .orElse(null);
    }

    public void useFishingRod(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        FishingHook bobber = findOurBobber();
        if (bobber != null) {
            if (!world.isClientSide()) {
                int i = bobber.retrieve(itemStack);
                itemStack.hurtAndBreak( i, user, EquipmentSlot.MAINHAND );
            }

            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
            user.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        } else {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
            if (!world.isClientSide()) {
                
                int i = EnchantmentVer.getEnchantmentLevel(Enchantments.LURE, itemStack, world.getServer());
                int j = EnchantmentVer.getEnchantmentLevel(Enchantments.LUCK_OF_THE_SEA, itemStack, world.getServer());
                
                world.addFreshEntity(new FishingHook(user, world, j, i));
            }

            user.gameEvent(GameEvent.ITEM_INTERACT_START);
        }

//        return TypedActionResult.success(itemStack, world.isClient());
    }
}