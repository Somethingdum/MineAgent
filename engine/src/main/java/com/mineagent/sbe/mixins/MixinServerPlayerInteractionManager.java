// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/mixins/MixinServerPlayerInteractionManager.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.mixins;

import com.mineagent.sbe.automatone.utils.accessor.IServerPlayerInteractionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixinServerPlayerInteractionManager implements IServerPlayerInteractionManager {
    private boolean automatone$brokeBlock;

    // SMH: Mojang's field names (Yarn's mining, miningPos and blockBreakingProgress).
    @Override
    @Accessor("isDestroyingBlock")
    public abstract boolean isMining();
    @Override
    @Accessor("destroyPos")
    public abstract BlockPos getMiningPos();
    @Accessor("lastSentState")
    public abstract int getBlockBreakingProgress();

    @Override
    public boolean automatone$hasBrokenBlock() {
        return this.automatone$brokeBlock;
    }

    @Inject(method = "handleBlockBreakAction", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;destroyPos:Lnet/minecraft/core/BlockPos;", opcode = Opcodes.PUTFIELD)) // SMH: Mojang's names
    private void setBrokeBlock(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int worldHeight, int i, CallbackInfo ci) {
        this.automatone$brokeBlock = true;
    }
}
