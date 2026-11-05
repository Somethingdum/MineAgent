// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/utils/player/EntityContext.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.utils.player;

import com.mineagent.sbe.altoclef.multiversion.ServerPlayerEntityVer;
import com.mineagent.sbe.automatone.api.BaritoneAPI;
import com.mineagent.sbe.automatone.api.cache.IWorldData;
import com.mineagent.sbe.automatone.api.pathing.calc.Avoidance;
import com.mineagent.sbe.automatone.api.utils.BetterBlockPos;
import com.mineagent.sbe.automatone.api.utils.IEntityContext;
import com.mineagent.sbe.automatone.api.utils.InteractionController;
import com.mineagent.sbe.automatone.api.utils.RayTraceUtils;
import com.mineagent.sbe.automatone.utils.accessor.ServerChunkManagerAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class EntityContext implements IEntityContext {

    private final ServerPlayer entity;
    private final InteractionController controller;
    private @Nullable Supplier<List<Avoidance>> avoidanceFinder;

    public EntityContext(ServerPlayer entity) {
        this.entity = entity;
        this.controller = new PlayerEntityInteractionController(entity);
    }

    @Override
    public ServerPlayer entity() {
        return this.entity;
    }

    @Override
    public @Nullable Inventory inventory() {
        return entity.getInventory();
    }

    @Override
    public InteractionController interactionController() {
        return controller;
    }

    @Override
    public ServerLevel world() {
        Level world = ServerPlayerEntityVer.getWorld(this.entity);
        if (world.isClientSide()) throw new IllegalStateException();
        return (ServerLevel) world;
    }

    @Override
    public IWorldData worldData() {
        return BaritoneAPI.getProvider().getBaritone(this.entity).getPlayerContext().worldData();
    }

    @Override
    public HitResult objectMouseOver() {
        return RayTraceUtils.rayTraceTowards(entity(), entityRotations(), interactionController().getBlockReachDistance());
    }

    @Override
    public BetterBlockPos feetPos() {
        // TODO find a better way to deal with soul sand!!!!!
        double x = entity().getX();
        double z = entity().getZ();
        BetterBlockPos feet = new BetterBlockPos(x, entity().getY() + 0.1251, z);

        ServerLevel world = world();
        if (world != null) {
            LevelChunk chunk = ((ServerChunkManagerAccessor) world.getChunkSource()).automatone$getChunkNow((int) x << 4, (int) z << 4);
            if (chunk != null && chunk.getBlockState(feet).getBlock() instanceof SlabBlock) {
                return feet.above();
            }
        }

        return feet;
    }

    private Stream<Entity> streamHostileEntities() {
        return this.worldEntitiesStream()
                .filter(entity -> entity instanceof Mob)
                .filter(entity -> (!(entity instanceof Spider)) || entity.getLightLevelDependentMagicValue() < 0.5)
                .filter(entity -> !(entity instanceof ZombifiedPiglin) || ((ZombifiedPiglin) entity).getLastHurtByMob() != null)
                .filter(entity -> !(entity instanceof EnderMan) || ((EnderMan) entity).isCreepy());
    }

    @Override
    public void setAvoidanceFinder(@Nullable Supplier<List<Avoidance>> avoidanceFinder) {
        this.avoidanceFinder = avoidanceFinder;
    }

    @Override
    public List<Avoidance> listAvoidedAreas() {
        if (!baritone().settings().avoidance.get()) {
            return Collections.emptyList();
        }

        if (this.avoidanceFinder != null) {
            return this.avoidanceFinder.get();
        }

        List<Avoidance> res = new ArrayList<>();
        double mobCoeff = baritone().settings().mobAvoidanceCoefficient.get();

        if (mobCoeff != 1.0D) {
            streamHostileEntities().forEach(entity -> res.add(new Avoidance(
                    entity.blockPosition(),
                    mobCoeff,
                    baritone().settings().mobAvoidanceRadius.get()
            )));
        }

        return res;
    }
}
