package me.sailex.altoclef.commands;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.commandsystem.Arg;
import me.sailex.altoclef.commandsystem.ArgParser;
import me.sailex.altoclef.commandsystem.Command;
import me.sailex.altoclef.commandsystem.CommandException;
import me.sailex.altoclef.tasks.movement.BodyLanguageTask;

public class BodyLanguageCommand extends Command {
    public BodyLanguageCommand() throws CommandException {
        super("bodylang",
                "Perform some sort of dance/body language action. Action must be either `greeting`, `nod_head`, `shake_head`, `victory` ",
                new Arg<>(String.class, "bodyLanguage"));
    }

    @Override
    protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
        String bodyLanguage = parser.get(String.class);
        mod.runUserTask(new BodyLanguageTask(bodyLanguage), () -> {
            System.out.println("Body language done");
            this.finish();
        });
    }

}