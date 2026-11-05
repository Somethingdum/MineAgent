// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/utils/player/PlayerEntityInteractionController.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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
import com.mineagent.sbe.altoclef.multiversion.world.HeightLimitViewVer;
import com.mineagent.sbe.automatone.api.utils.InteractionController;
import com.mineagent.sbe.automatone.utils.accessor.IServerPlayerInteractionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;


/**
 * Implementation of {@link InteractionController} that chains to the primary player controller's methods
 *
 * @author Brady
 * @since 12/14/2018
 */
public class PlayerEntityInteractionController implements InteractionController {

    private final ServerPlayer player;
    private int sequence;

    public PlayerEntityInteractionController(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public boolean hasBrokenBlock() {
        return ((IServerPlayerInteractionManager) this.player.gameMode).automatone$hasBrokenBlock();
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        IServerPlayerInteractionManager interactionManager = (IServerPlayerInteractionManager) this.player.gameMode;
        if (interactionManager.isMining()) {
            int progress = interactionManager.getBlockBreakingProgress();
            if (progress >= 10) {
                this.player.gameMode.handleBlockBreakAction(interactionManager.getMiningPos(),
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, side,
                        HeightLimitViewVer.getTopY(ServerPlayerEntityVer.getWorld(this.player)), sequence++);
            }
            return true;
        }
        return false;
    }

    @Override
    public void resetBlockRemoving() {
        IServerPlayerInteractionManager interactionManager = (IServerPlayerInteractionManager) this.player.gameMode;
        if (interactionManager.isMining()) {
            this.player.gameMode.handleBlockBreakAction(interactionManager.getMiningPos(),
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.UP,
                    HeightLimitViewVer.getTopY(ServerPlayerEntityVer.getWorld(this.player)), sequence++);
        }
    }

    @Override
    public GameType getGameType() {
        return player.gameMode.getGameModeForPlayer();
    }

    @Override
    public InteractionResult processRightClickBlock(Player player, Level world, InteractionHand hand, BlockHitResult result) {
        return this.player.gameMode.useItemOn(this.player, ServerPlayerEntityVer.getWorld(this.player),
                this.player.getItemInHand(hand), hand, result);
    }

    @Override
    public InteractionResult processRightClick(Player player, Level world, InteractionHand hand) {
        return this.player.gameMode.useItem(this.player, ServerPlayerEntityVer.getWorld(this.player),
                this.player.getItemInHand(hand), hand);
    }

    @Override
    public boolean clickBlock(BlockPos loc, Direction face) {
        BlockState state = ServerPlayerEntityVer.getWorld(this.player).getBlockState(loc);
        if (state.isAir())
            return false;

        this.player.gameMode.handleBlockBreakAction(loc, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                face, HeightLimitViewVer.getTopY(ServerPlayerEntityVer.getWorld(this.player)), sequence++);
        // Success = starting the mining process or insta-mining
        return ((IServerPlayerInteractionManager) this.player.gameMode).isMining()
                || ServerPlayerEntityVer.getWorld(this.player).isEmptyBlock(loc);
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
        // NO-OP
    }

    @Override
    public double getBlockReachDistance() {
        return this.getGameType().isCreative() ? 5.0 : 4.5;
    }
}
