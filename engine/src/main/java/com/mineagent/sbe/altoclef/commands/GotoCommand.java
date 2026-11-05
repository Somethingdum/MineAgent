// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/GotoCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.commandsystem.Arg;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;
import com.mineagent.sbe.altoclef.commandsystem.GotoTarget;
import com.mineagent.sbe.altoclef.tasks.movement.DefaultGoToDimensionTask;
import com.mineagent.sbe.altoclef.tasks.movement.GetToBlockTask;
import com.mineagent.sbe.altoclef.tasks.movement.GetToXZTask;
import com.mineagent.sbe.altoclef.tasks.movement.GetToYTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class GotoCommand extends Command {
   public GotoCommand() throws CommandException {
      super(
         "goto",
         "Tell bot to travel to a set of coordinates",
         new Arg<>(GotoTarget.class, "[x y z dimension]/[x z dimension]/[y dimension]/[dimension]/[x y z]/[x z]/[y]")
      );
   }

   public static Task getMovementTaskFor(GotoTarget target) {
      return (Task)(switch (target.getType()) {
         case XYZ -> new GetToBlockTask(new BlockPos(target.getX(), target.getY(), target.getZ()), target.getDimension());
         case XZ -> new GetToXZTask(target.getX(), target.getZ(), target.getDimension());
         case Y -> new GetToYTask(target.getY(), target.getDimension());
         case NONE -> new DefaultGoToDimensionTask(target.getDimension());
      });
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      GotoTarget target = parser.get(GotoTarget.class);
      mod.runUserTask(getMovementTaskFor(target), () -> this.finish());
   }
}
