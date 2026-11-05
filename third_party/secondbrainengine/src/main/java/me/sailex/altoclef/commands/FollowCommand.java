package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.Arg;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.tasks.movement.FollowPlayerTask;

public class FollowCommand extends Command {
   public FollowCommand() throws CommandException {
      super(
         "follow", "Follows you or someone else. Example: `follow Player` to follow player with username=Player", new Arg<>(String.class, "username", null, 0)
      );
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      String username = parser.get(String.class);
      if (username == null) {
         if (mod.getOwner() == null) {
            mod.logWarning("No butler user currently present. Running this command with no user argument can ONLY be done via butler.");
            this.finish();
            return;
         }

         username = mod.getOwner().getName().getString();
      }

      mod.runUserTask(new FollowPlayerTask(username), () -> this.finish());
   }
}
