// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/utils/IEntityContext.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.utils;

import com.mineagent.sbe.automatone.api.BaritoneAPI;
import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.cache.IWorldData;
import com.mineagent.sbe.automatone.api.pathing.calc.Avoidance;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Brady
 * @since 11/12/2018
 */
public interface IEntityContext {

    ServerPlayer entity();

    default IBaritone baritone() {
        return BaritoneAPI.getProvider().getBaritone(this.entity());
    }

    @Nullable Inventory inventory();

    InteractionController interactionController();

    ServerLevel world();

    default Iterable<Entity> worldEntities() {
        return world().getAllEntities();
    }

    default Stream<Entity> worldEntitiesStream() {
        return StreamSupport.stream(worldEntities().spliterator(), false);
    }

    void setAvoidanceFinder(@Nullable Supplier<List<Avoidance>> avoidanceFinder);

    List<Avoidance> listAvoidedAreas();

    IWorldData worldData();

    HitResult objectMouseOver();

    BetterBlockPos feetPos();

    default Vec3 feetPosAsVec() {
        return new Vec3(entity().getX(), entity().getY(), entity().getZ());
    }

    default Vec3 headPos() {
        return new Vec3(entity().getX(), entity().getY() + entity().getEyeHeight(), entity().getZ());
    }

    default Rotation entityRotations() {
        return new Rotation(entity().getYRot(), entity().getXRot());
    }

    /**
     * Returns the block that the crosshair is currently placed over. Updated once per tick.
     *
     * @return The position of the highlighted block
     */
    default Optional<BlockPos> getSelectedBlock() {
        HitResult result = objectMouseOver();
        if (result != null && result.getType() == HitResult.Type.BLOCK) {
            return Optional.of(((BlockHitResult) result).getBlockPos());
        }
        return Optional.empty();
    }

    default boolean isLookingAt(BlockPos pos) {
        return getSelectedBlock().equals(Optional.of(pos));
    }

    default void logDebug(String message) {
        if (!baritone().settings().chatDebug.get()) { // SMH: this body's own (plan 71, step 1.7)
            return;
        }
        com.mineagent.sbe.common.ServerHooks.log(entity(), message); // SMH
        LivingEntity entity = entity();
        if (entity instanceof Player) ((Player) entity).displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GRAY), false);

        if (!BaritoneAPI.getGlobalSettings().syncWithOps.get()) return;

        MinecraftServer server = world().getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp( p.getGameProfile() )) {
                BaritoneAPI.getProvider().getBaritone(this.entity()).logDirect(message);
            }
        }
    }
}
