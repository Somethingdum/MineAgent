// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/command/defaults/SurfaceCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.command.defaults;

import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.command.Command;
import com.mineagent.sbe.automatone.api.command.argument.IArgConsumer;
import com.mineagent.sbe.automatone.api.command.exception.CommandException;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalBlock;
import com.mineagent.sbe.automatone.api.utils.BetterBlockPos;
import com.mineagent.sbe.automatone.api.utils.IEntityContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.AirBlock;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class SurfaceCommand extends Command {

    protected SurfaceCommand() {
        super("surface", "top");
    }

    @Override
    public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
        IEntityContext ctx = baritone.getPlayerContext();
        final BetterBlockPos playerPos = ctx.feetPos();
        final int surfaceLevel = ctx.world().getSeaLevel();
        final int worldHeight = ctx.world().getHeight();

        // Ensure this command will not run if you are above the surface level and the block above you is air
        // As this would imply that your are already on the open surface
        if (playerPos.getY() > surfaceLevel && ctx.world().getBlockState(playerPos.above()).getBlock() instanceof AirBlock) {
            logDirect(source, "Already at surface");
            return;
        }

        final int startingYPos = Math.max(playerPos.getY(), surfaceLevel);

        for (int currentIteratedY = startingYPos; currentIteratedY < worldHeight; currentIteratedY++) {
            final BetterBlockPos newPos = new BetterBlockPos(playerPos.getX(), currentIteratedY, playerPos.getZ());

            if (!(ctx.world().getBlockState(newPos).getBlock() instanceof AirBlock) && newPos.getY() > playerPos.getY()) {
                Goal goal = new GoalBlock(newPos.above());
                logDirect(source, String.format("Going to: %s", goal.toString()));
                baritone.getCustomGoalProcess().setGoalAndPath(goal);
                return;
            }
        }
        logDirect(source, "No higher location found");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Used to get out of caves, mines, ...";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The surface/top command makes an entity head towards the closest surface-like area.",
                "",
                "This can be the surface or the highest available air space, depending on circumstances.",
                "",
                "Usage:",
                "> surface - Used to get out of caves, mines, ...",
                "> top - Used to get out of caves, mines, ..."
        );
    }
}
