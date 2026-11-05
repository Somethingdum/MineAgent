// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/api/java/baritone/api/utils/IPlayerContext.java
// at commit 11ea66c (branch 1.21.1). SMH: the companion's server-side body in place of the client's player; no world data.
package com.mineagent.engine.alto.baritone.api.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface IPlayerContext {

    ServerPlayer player();

    IPlayerController playerController();

    ServerLevel world();

    default Iterable<Entity> entities() {
        return world().getAllEntities();
    }

    default Stream<Entity> entitiesStream() {
        return StreamSupport.stream(entities().spliterator(), false);
    }

    HitResult objectMouseOver();

    default BetterBlockPos playerFeet() {
        // TODO find a better way to deal with soul sand!!!!!
        BetterBlockPos feet = new BetterBlockPos(player().position().x, player().position().y + 0.1251, player().position().z);
        try {
            if (world().getBlockState(feet).getBlock() instanceof SlabBlock) {
                return feet.above();
            }
        } catch (NullPointerException ignored) {}
        return feet;
    }

    default Vec3 playerFeetAsVec() {
        return new Vec3(player().position().x, player().position().y, player().position().z);
    }

    default Vec3 playerHead() {
        return new Vec3(player().position().x, player().position().y + player().getEyeHeight(), player().position().z);
    }

    default Vec3 playerMotion() {
        return player().getDeltaMovement();
    }

    default Rotation playerRotations() {
        return new Rotation(player().getYRot(), player().getXRot());
    }

    @Deprecated
    static double eyeHeight(boolean ifSneaking) {
        return ifSneaking ? 1.27 : 1.62;
    }

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
}
