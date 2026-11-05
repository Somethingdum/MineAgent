package me.sailex.altoclef;

import me.sailex.altoclef.commands.AttackPlayerOrMobCommand;
import me.sailex.altoclef.commands.BodyLanguageCommand;
import me.sailex.altoclef.commands.DepositCommand;
import me.sailex.altoclef.commands.EquipCommand;
import me.sailex.altoclef.commands.FarmCommand;
import me.sailex.altoclef.commands.FishCommand;
import me.sailex.altoclef.commands.FollowCommand;
import me.sailex.altoclef.commands.FoodCommand;
import me.sailex.altoclef.commands.GamerCommand;
import me.sailex.altoclef.commands.GetCommand;
import me.sailex.altoclef.commands.GiveCommand;
import me.sailex.altoclef.commands.GotoCommand;
import me.sailex.altoclef.commands.HeroCommand;
import me.sailex.altoclef.commands.IdleCommand;
import me.sailex.altoclef.commands.LocateStructureCommand;
import me.sailex.altoclef.commands.MeatCommand;
import me.sailex.altoclef.commands.ReloadSettingsCommand;
import me.sailex.altoclef.commands.StopCommand;
import me.sailex.altoclef.commands.random.ScanCommand;
import me.sailex.altoclef.commandsystem.CommandException;

public class AltoClefCommands {
   public static void init(AltoClefController controller) throws CommandException {
      controller.getCommandExecutor()
         .registerNewCommand(
            new GetCommand(),
            new EquipCommand(),
            new BodyLanguageCommand(),
            new DepositCommand(),
            new GotoCommand(),
            new IdleCommand(),
            new HeroCommand(),
            new LocateStructureCommand(),
            new StopCommand(),
            new FoodCommand(),
            new MeatCommand(),
            new ReloadSettingsCommand(),
            new GamerCommand(),
            new FollowCommand(),
            new GiveCommand(),
            new ScanCommand(),
            new AttackPlayerOrMobCommand(),
            new FarmCommand(),
            new FishCommand()
         );
   }
}
