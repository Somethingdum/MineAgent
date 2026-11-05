// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/AltoClefCommands.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef;

import com.mineagent.sbe.altoclef.commands.AttackPlayerOrMobCommand;
import com.mineagent.sbe.altoclef.commands.BodyLanguageCommand;
import com.mineagent.sbe.altoclef.commands.DepositCommand;
import com.mineagent.sbe.altoclef.commands.EquipCommand;
import com.mineagent.sbe.altoclef.commands.FarmCommand;
import com.mineagent.sbe.altoclef.commands.FishCommand;
import com.mineagent.sbe.altoclef.commands.FollowCommand;
import com.mineagent.sbe.altoclef.commands.FoodCommand;
import com.mineagent.sbe.altoclef.commands.GamerCommand;
import com.mineagent.sbe.altoclef.commands.GetCommand;
import com.mineagent.sbe.altoclef.commands.GiveCommand;
import com.mineagent.sbe.altoclef.commands.GotoCommand;
import com.mineagent.sbe.altoclef.commands.HeroCommand;
import com.mineagent.sbe.altoclef.commands.IdleCommand;
import com.mineagent.sbe.altoclef.commands.LocateStructureCommand;
import com.mineagent.sbe.altoclef.commands.MeatCommand;
import com.mineagent.sbe.altoclef.commands.ReloadSettingsCommand;
import com.mineagent.sbe.altoclef.commands.StopCommand;
import com.mineagent.sbe.altoclef.commands.random.ScanCommand;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;

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
