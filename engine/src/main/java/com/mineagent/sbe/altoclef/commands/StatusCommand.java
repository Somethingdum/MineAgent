// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/StatusCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.tasksystem.Task;

import java.util.List;

public class StatusCommand extends Command {
   public StatusCommand() {
      super("status", "Get status of currently executing command");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      List<Task> tasks = mod.getUserTaskChain().getTasks();
      if (tasks.isEmpty()) {
         mod.log("No tasks currently running.");
      } else {
         mod.log("CURRENT TASK: " + tasks.get(0).toString());
      }

      this.finish();
   }
}
