// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/blockpos/BlockPosVer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion.blockpos;

import com.mineagent.engine.alto.multiversion.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import com.mineagent.engine.alto.multiversion.blockpos.BlockPosHelper;

public class BlockPosVer {


    public static BlockPos ofFloored(Position pos) {
        return new BlockPos(Mth.floor(pos.x()), Mth.floor(pos.y()), Mth.floor(pos.z()));
    }


    public static double getSquaredDistance(BlockPos pos, Position obj) {
        return pos.distToCenterSqr(obj);
    }

    @Pattern
    private static Vec3i north(Vec3i blockPos) {
        return blockPos.north();
    }

    @Pattern
    private static Vec3i north(Vec3i blockPos, int amount) {
        return blockPos.north(amount);
    }

    @Pattern
    private static Vec3i east(Vec3i blockPos) {
        return blockPos.east();
    }

    @Pattern
    private static Vec3i east(Vec3i blockPos, int amount) {
        return blockPos.east(amount);
    }

    @Pattern
    private static Vec3i west(Vec3i blockPos) {
        return blockPos.west();
    }

    @Pattern
    private static Vec3i west(Vec3i blockPos, int amount) {
        return blockPos.west(amount);
    }

    @Pattern
    private static Vec3i south(Vec3i blockPos) {
        return blockPos.south();
    }

    @Pattern
    private static Vec3i south(Vec3i blockPos, int amount) {
        return blockPos.south(amount);
    }

    @Pattern
    private static Vec3i add(Vec3i blockPos, int x, int y, int z) {
        return blockPos.offset(x,y,z);
    }


}
