// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/random/DummyTaskCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands.random;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;
import com.mineagent.sbe.altoclef.tasksystem.Task;

public class DummyTaskCommand extends Command {
   public DummyTaskCommand() {
      super("dummy", "Doesnt do anything");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      mod.runUserTask(new DummyTask(), () -> this.finish());
   }

   private class DummyTask extends Task {
      @Override
      protected void onStart() {
      }

      @Override
      protected Task onTick() {
         return null;
      }

      @Override
      protected void onStop(Task interruptTask) {
      }

      @Override
      protected boolean isEqual(Task other) {
         return false;
      }

      @Override
      protected String toDebugString() {
         return null;
      }
   }
}
