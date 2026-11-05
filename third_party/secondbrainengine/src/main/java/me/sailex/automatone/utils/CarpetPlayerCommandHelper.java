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

package me.sailex.automatone.utils;

import com.mojang.authlib.GameProfile;
import me.sailex.altoclef.multiversion.CommandManagerVer;
import me.sailex.altoclef.multiversion.ServerPlayerEntityVer;
import me.sailex.automatone.Baritone;
import me.sailex.automatone.api.IBaritone;
import me.sailex.automatone.api.utils.ICommandHelper;
import me.sailex.automatone.api.utils.MoveDirection;
import carpet.fakes.ServerPlayerInterface;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class CarpetPlayerCommandHelper implements ICommandHelper {

    private final IBaritone baritone;
    private final Commands commandManager;
    private final CommandSourceStack commandSource;
    private String playerCommand;

    public CarpetPlayerCommandHelper(Baritone baritone, ServerPlayer player) {
        this.baritone = baritone;
        MinecraftServer server = ServerPlayerEntityVer.getWorld(player).getServer();
        this.commandManager = server.getCommands();
        this.commandSource = server.createCommandSourceStack();
    }

    private void buildPlayerCommand() {
        GameProfile profile = baritone.getPlayerContext().entity().getGameProfile();
        buildPlayerCommand( profile.getName() );
    }

    private void buildPlayerCommand(String playerName) {
        this.playerCommand = "player " + playerName;
    }

    @Override
    public void executeMove(MoveDirection moveDirection) {
        buildPlayerCommand();
        CommandManagerVer.executeWithPrefix(commandManager, commandSource, playerCommand + " move " + moveDirection.toString().toLowerCase());
    }

    @Override
    public void executeMoveStop() {
        buildPlayerCommand();
        CommandManagerVer.executeWithPrefix(commandManager, commandSource, playerCommand + " move");
    }

    @Override
    public void executeSpawn(String username) {
        buildPlayerCommand(username);
        CommandManagerVer.executeWithPrefix(commandManager, commandSource, playerCommand + " spawn");
    }

    @Override
    public void executeLook(Direction lookDirection) {
        buildPlayerCommand();
        CommandManagerVer.executeWithPrefix(commandManager, commandSource, playerCommand + " look " + lookDirection.name().toLowerCase());
    }

    @Override
    public void executeSneak() {
        buildPlayerCommand();
        CommandManagerVer.executeWithPrefix(commandManager, commandSource, playerCommand + " sneak");
    }

    @Override
    public void executeUnSneak() {
        buildPlayerCommand();
        CommandManagerVer.executeWithPrefix(commandManager, commandSource, playerCommand + " unsneak");
    }

    @Override
    public void executeJump() {
        buildPlayerCommand();
        CommandManagerVer.executeWithPrefix(commandManager, commandSource, playerCommand + " jump");
    }

    @Override
    public void executeAttack() {
        buildPlayerCommand();
        CommandManagerVer.executeWithPrefix(commandManager, commandSource, playerCommand + " attack");
    }

    @Override
    public void executeDrop(int slot, boolean dropAll) {
        ServerPlayerInterface player = ((ServerPlayerInterface) baritone.getPlayerContext().entity());
        player.getActionPack().drop(slot, dropAll);
    }

    @Override
    public void executeUse() {
        buildPlayerCommand();
        CommandManagerVer.executeWithPrefix(commandManager, commandSource, playerCommand + " use");
    }

    @Override
    public void executeStopAll() {
        buildPlayerCommand();
        CommandManagerVer.executeWithPrefix(commandManager, commandSource, playerCommand + " stop");
    }
}
