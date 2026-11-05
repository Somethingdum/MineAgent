// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/utils/player/DummyEntityController.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

import com.mineagent.sbe.automatone.api.utils.InteractionController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A stubbed controller implementation for entities that cannot break or place blocks
 */
public class DummyEntityController implements InteractionController {
    public static final DummyEntityController INSTANCE = new DummyEntityController();

    @Override
    public boolean hasBrokenBlock() {
        return false;
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        return false;
    }

    @Override
    public void resetBlockRemoving() {
        // NO-OP
    }

    @Override
    public GameType getGameType() {
        return GameType.ADVENTURE;
    }

    @Override
    public InteractionResult processRightClickBlock(Player player, Level world, InteractionHand hand, BlockHitResult result) {
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResult processRightClick(Player player, Level world, InteractionHand hand) {
        return InteractionResult.FAIL;
    }

    @Override
    public boolean clickBlock(BlockPos loc, Direction face) {
        return false;
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
        // NO-OP
    }

    @Override
    public double getBlockReachDistance() {
        return 0;
    }
}
