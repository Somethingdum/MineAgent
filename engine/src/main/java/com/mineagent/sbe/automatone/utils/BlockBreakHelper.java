// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/utils/BlockBreakHelper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.utils;

import com.mineagent.sbe.automatone.api.utils.IEntityContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Brady
 * @since 8/25/2018
 */
public final class BlockBreakHelper {

    private final IEntityContext ctx;
    private @Nullable BlockPos lastPos;

    BlockBreakHelper(IEntityContext ctx) {
        this.ctx = ctx;
    }

    public void stopBreakingBlock() {
        // The player controller will never be null, but the player can be
        if (ctx.entity() != null && lastPos != null) {
            if (!ctx.interactionController().hasBrokenBlock()) {
                // insane bypass to check breaking succeeded
                ctx.interactionController().setHittingBlock(true);
            }
            ctx.interactionController().resetBlockRemoving();
            lastPos = null;
        }
    }

    public void tick(boolean isLeftClick) {
        HitResult trace = ctx.objectMouseOver();
        boolean isBlockTrace = trace != null && trace.getType() == HitResult.Type.BLOCK;

        if (isLeftClick && isBlockTrace) {
            BlockPos pos = ((BlockHitResult) trace).getBlockPos();
            if (!Objects.equals(lastPos, pos)) {
                ctx.interactionController().clickBlock(pos, ((BlockHitResult) trace).getDirection());
                ctx.entity().swing(InteractionHand.MAIN_HAND);
            }

            // Attempt to break the block
            if (ctx.interactionController().onPlayerDamageBlock(pos, ((BlockHitResult) trace).getDirection())) {
                ctx.entity().swing(InteractionHand.MAIN_HAND);
            }

            ctx.interactionController().setHittingBlock(false);

            lastPos = pos;
        } else if (lastPos != null) {
            stopBreakingBlock();
            lastPos = null;
        }
    }
}
