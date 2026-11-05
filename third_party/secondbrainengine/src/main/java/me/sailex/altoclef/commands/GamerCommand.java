package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.tasks.speedrun.beatgame.BeatMinecraftTask;

public class GamerCommand extends Command {
   public GamerCommand() {
      super("gamer", "Beats the game (Miran version)");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      mod.runUserTask(new BeatMinecraftTask(mod), () -> this.finish());
   }
}
