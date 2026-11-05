// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/StopCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;

public class StopCommand extends Command {
   public StopCommand() {
      super("stop", "Stop task runner (stops all automation), also stops the IDLE task until a new task is started");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      mod.stop();
      this.finish();
   }
}
