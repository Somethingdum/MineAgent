package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.util.helpers.ConfigHelper;

public class ReloadSettingsCommand extends Command {
   public ReloadSettingsCommand() {
      super("reload_settings", "Reloads bot settings and butler whitelist/blacklist.");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      ConfigHelper.reloadAllConfigs();
      mod.log("Reload successful!");
      this.finish();
   }
}
