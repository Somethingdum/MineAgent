/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.sailex.automatone.command.defaults;

import me.sailex.altoclef.multiversion.CommandVer;
import me.sailex.automatone.Automatone;
import me.sailex.automatone.api.BaritoneAPI;
import me.sailex.automatone.api.IBaritone;
import me.sailex.automatone.api.Settings;
import me.sailex.automatone.api.command.Command;
import me.sailex.automatone.api.command.argument.IArgConsumer;
import me.sailex.automatone.api.command.exception.CommandException;
import me.sailex.automatone.api.command.exception.CommandInvalidTypeException;
import me.sailex.automatone.api.command.helpers.Paginator;
import me.sailex.automatone.api.command.helpers.TabCompleteHelper;
import me.sailex.automatone.api.utils.SettingsUtil;
import me.sailex.automatone.command.argument.ArgConsumer;
import me.sailex.automatone.utils.SettingsLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.sailex.automatone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;
import static me.sailex.automatone.api.utils.SettingsUtil.settingDefaultToString;
import static me.sailex.automatone.api.utils.SettingsUtil.settingTypeToString;
import static me.sailex.automatone.api.utils.SettingsUtil.settingValueToString;

public class SetCommand extends Command {

    public SetCommand() {
        super("set", "setting", "settings");
    }

    @Override
    public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
        Settings settings;
        boolean global;
        String arg = args.hasAny() ? args.getString().toLowerCase(Locale.ROOT) : "list";
        if (Arrays.asList("g", "global").contains(arg.toLowerCase(Locale.ROOT))) {
            settings = BaritoneAPI.getGlobalSettings();
            arg = args.hasAny() ? args.getString().toLowerCase(Locale.ROOT) : arg;
            global = true;
        } else {
            settings = baritone.settings();
            global = false;
        }
        if (Arrays.asList("s", "save").contains(arg)) {
            SettingsLoader.save(settings);
            logDirect(source, "Settings saved");
            return;
        }
        boolean viewModified = Arrays.asList("m", "mod", "modified").contains(arg);
        boolean viewAll = Arrays.asList("all", "l", "list").contains(arg);
        boolean paginate = viewModified || viewAll;
        if (paginate) {
            String search = args.hasAny() && args.peekAsOrNull(Integer.class) == null ? args.getString() : "";
            args.requireMax(1);
            List<? extends Settings.Setting<?>> toPaginate =
                    (viewModified ? SettingsUtil.modifiedSettings(settings) : settings.allSettings).stream()
                            .filter(s -> !s.getName().equals("logger"))
                            .filter(s -> s.getName().toLowerCase(Locale.US).contains(search.toLowerCase(Locale.US)))
                            .sorted((s1, s2) -> String.CASE_INSENSITIVE_ORDER.compare(s1.getName(), s2.getName()))
                            .collect(Collectors.toList());
            Paginator.paginate(
                    args,
                    new Paginator<>(source, toPaginate),
                    () -> logDirect(
                            source, !search.isEmpty()
                                    ? String.format("All %ssettings containing the string '%s':", viewModified ? "modified " : "", search)
                                    : String.format("All %ssettings:", viewModified ? "modified " : "")
                    ),
                    setting -> {
                        MutableComponent typeComponent = Component.literal(String.format(
                                " (%s)",
                                settingTypeToString(setting)
                        ));
                        typeComponent.setStyle(typeComponent.getStyle().applyFormat(ChatFormatting.DARK_GRAY));
                        MutableComponent hoverComponent = Component.literal("");
                        hoverComponent.setStyle(hoverComponent.getStyle().applyFormat(ChatFormatting.GRAY));
                        hoverComponent.append(setting.getName());
                        hoverComponent.append(String.format("\nType: %s", settingTypeToString(setting)));
                        hoverComponent.append(String.format("\n\nValue:\n%s", settingValueToString(setting)));
                        hoverComponent.append(String.format("\n\nDefault Value:\n%s", settingDefaultToString(setting)));
                        String commandSuggestion = FORCE_COMMAND_PREFIX + String.format("set %s%s ", global ? "global " : "", setting.getName());
                        MutableComponent component = Component.literal(setting.getName());
                        component.setStyle(component.getStyle().applyFormat(ChatFormatting.GRAY));
                        component.append(typeComponent);
                        component.setStyle(component.getStyle()
                                .withHoverEvent(CommandVer.getShowText(hoverComponent))
                                .withClickEvent(CommandVer.getSuggestCommand(commandSuggestion)));
                        return component;
                    },
                    FORCE_COMMAND_PREFIX + "set " + arg + " " + search
            );
            return;
        }
        args.requireMax(1);
        boolean resetting = arg.equalsIgnoreCase("reset");
        boolean toggling = arg.equalsIgnoreCase("toggle");
        boolean doingSomething = resetting || toggling;
        if (resetting) {
            if (!args.hasAny()) {
                logDirect(source, "Please specify 'all' as an argument to reset to confirm you'd really like to do this");
                logDirect(source, "ALL settings will be reset. Use the 'set modified' or 'modified' commands to see what will be reset.");
                logDirect(source, "Specify a setting name instead of 'all' to only reset one setting");
            } else if (args.peekString().equalsIgnoreCase("all")) {
                SettingsUtil.modifiedSettings(settings).forEach(Settings.Setting::reset);
                logDirect(source, "All settings have been reset to their default values");
                SettingsLoader.save(settings);
                return;
            }
        }
        if (toggling) {
            args.requireMin(1);
        }
        String settingName = doingSomething ? args.getString() : arg;
        Settings.Setting<?> setting = settings.allSettings.stream()
                .filter(s -> s.getName().equalsIgnoreCase(settingName))
                .findFirst()
                .orElse(null);
        if (setting == null) {
            throw new CommandInvalidTypeException(args.consumed(), "a valid setting");
        }
        if (!doingSomething && !args.hasAny()) {
            logDirect(source, String.format("Value of setting %s:", setting.getName()));
            logDirect(source, settingValueToString(setting));
        } else {
            String oldValue = settingValueToString(setting);
            if (resetting) {
                setting.reset();
            } else if (toggling) {
                if (setting.getValueClass() != Boolean.class) {
                    throw new CommandInvalidTypeException(args.consumed(), "a toggleable setting", "some other setting");
                }
                @SuppressWarnings("unchecked") Settings.Setting<Boolean> toggle = (Settings.Setting<Boolean>) setting;
                toggle.set(!toggle.get());
                logDirect(source, String.format(
                        "Toggled setting %s to %s",
                        toggle.getName(),
                        toggle.get()
                ));
            } else {
                String newValue = args.getString();
                try {
                    SettingsUtil.parseAndApply(settings, arg, newValue);
                } catch (Throwable t) {
                    Automatone.LOGGER.error(t);
                    throw new CommandInvalidTypeException(args.consumed(), "a valid value", t);
                }
            }
            if (!toggling) {
                logDirect(source, String.format(
                        "Successfully %s %s to %s",
                        resetting ? "reset" : "set",
                        setting.getName(),
                        settingValueToString(setting)
                ));
            }
            MutableComponent oldValueComponent = Component.literal(String.format("Old value: %s", oldValue));
            oldValueComponent.setStyle(oldValueComponent.getStyle()
                    .applyFormat(ChatFormatting.GRAY)
                    .withHoverEvent(CommandVer.getShowText(Component.literal("Click to set the setting back to this value")))
                    .withClickEvent(CommandVer.getRunCommand(FORCE_COMMAND_PREFIX + String.format("set %s %s", setting.getName(), oldValue))));
            logDirect(source, oldValueComponent);
        }
        SettingsLoader.save(settings);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        Settings settings = ((ArgConsumer) args).getBaritone().settings();

        if (args.hasAny()) {
            String arg = args.getString();
            if (Arrays.asList("g", "global").contains(arg.toLowerCase(Locale.ROOT))) {
                if (args.hasAny()) {
                    arg = args.getString();
                } else {
                    return new TabCompleteHelper()
                            .addSettings(settings)
                            .sortAlphabetically()
                            .prepend("list", "modified", "reset", "toggle", "save")
                            .filterPrefix(arg)
                            .stream();
                }
            }
            if (args.hasExactlyOne() && !Arrays.asList("s", "save").contains(args.peekString().toLowerCase(Locale.ROOT))) {
                if (arg.equalsIgnoreCase("reset")) {
                    return new TabCompleteHelper()
                            .addModifiedSettings(settings)
                            .prepend("all")
                            .filterPrefix(args.getString())
                            .stream();
                } else if (arg.equalsIgnoreCase("toggle")) {
                    return new TabCompleteHelper()
                            .addToggleableSettings(settings)
                            .filterPrefix(args.getString())
                            .stream();
                }
                Settings.Setting<?> setting = settings.byLowerName.get(arg.toLowerCase(Locale.US));
                if (setting != null) {
                    if (setting.getType() == Boolean.class) {
                        TabCompleteHelper helper = new TabCompleteHelper();
                        if ((Boolean) setting.get()) {
                            helper.append("true", "false");
                        } else {
                            helper.append("false", "true");
                        }
                        return helper.filterPrefix(args.getString()).stream();
                    } else {
                        return Stream.of(settingValueToString(setting));
                    }
                }
            } else if (!args.hasAny()) {
                return new TabCompleteHelper()
                        .addSettings(settings)
                        .sortAlphabetically()
                        .prepend("list", "modified", "reset", "toggle", "save")
                        .prepend("global")
                        .filterPrefix(arg)
                        .stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View or change settings";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Using the set command, you can manage all of Baritone's settings. Almost every aspect is controlled by these settings - go wild!",
                "",
                "Usage:",
                "> set - Same as `set list`",
                "> set list [page] - View all settings",
                "> set modified [page] - View modified settings",
                "> set <setting> - View the current value of a setting",
                "> set <setting> <value> - Set the value of a setting",
                "> set reset all - Reset ALL SETTINGS to their defaults",
                "> set reset <setting> - Reset a setting to its default",
                "> set toggle <setting> - Toggle a boolean setting",
                "> set save - Save all settings (this is automatic tho)"
        );
    }
}
