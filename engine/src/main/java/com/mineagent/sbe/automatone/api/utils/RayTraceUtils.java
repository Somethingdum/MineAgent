// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/utils/RayTraceUtils.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.mineagent.sbe.automatone.api.utils.RotationUtils.getCameraPosVec;

/**
 * @author Brady
 * @since 8/25/2018
 */
public final class RayTraceUtils {

    public static ClipContext.Fluid fluidHandling = ClipContext.Fluid.NONE;

    private RayTraceUtils() {}

    /**
     * Performs a block raytrace with the specified rotations. This should only be used when
     * any entity collisions can be ignored, because this method will not recognize if an
     * entity is in the way or not. The local player's block reach distance will be used.
     *
     * @param entity             The entity representing the raytrace source
     * @param rotation           The rotation to raytrace towards
     * @param blockReachDistance The block reach distance of the entity
     * @return The calculated raytrace result
     */
    public static HitResult rayTraceTowards(Entity entity, Rotation rotation, double blockReachDistance) {
        return rayTraceTowards(entity, rotation, blockReachDistance, false);
    }

    public static HitResult rayTraceTowards(Entity entity, Rotation rotation, double blockReachDistance, boolean wouldSneak) {
        Vec3 start;
        if (wouldSneak) {
            start = inferSneakingEyePosition(entity);
        } else {
            start = getCameraPosVec(entity); // do whatever is correct
        }
        Vec3 direction = RotationUtils.calcVector3dFromRotation(rotation);
        Vec3 end = start.add(
                direction.x * blockReachDistance,
                direction.y * blockReachDistance,
                direction.z * blockReachDistance
        );
        return EntityVer.getWorld(entity).clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, fluidHandlingOf(entity), entity)); // SMH: AltoClef 0002
    }

    // SMH: the companion's own fluid handling; any other entity's ray stops at no fluid (never makes a Baritone).
    private static ClipContext.Fluid fluidHandlingOf(Entity entity) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player
                && com.mineagent.sbe.automatone.api.BaritoneAPI.getProvider() instanceof com.mineagent.sbe.automatone.BaritoneProvider provider) {
            com.mineagent.sbe.automatone.api.IBaritone baritone = provider.playerToBaritone.get(player);
            if (baritone != null) return ((com.mineagent.sbe.automatone.Baritone) baritone).getExtraBaritoneSettings().getRayFluidHandling();
        }
        return ClipContext.Fluid.NONE;
    }

    public static Vec3 inferSneakingEyePosition(Entity entity) {
        return new Vec3(entity.getX(), entity.getY() + entity.getEyeHeight(Pose.CROUCHING), entity.getZ());
    }
}
