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

package me.sailex.automatone;

import me.sailex.altoclef.multiversion.EntityVer;
import me.sailex.automatone.api.BaritoneAPI;
import me.sailex.automatone.api.IBaritone;
import me.sailex.automatone.api.Settings;
import me.sailex.automatone.api.event.listener.IEventBus;
import me.sailex.automatone.api.process.IBaritoneProcess;
import me.sailex.automatone.api.selection.ISelectionManager;
import me.sailex.automatone.api.utils.ICommandHelper;
import me.sailex.automatone.api.utils.IEntityContext;
import me.sailex.automatone.behavior.Behavior;
import me.sailex.automatone.behavior.InventoryBehavior;
import me.sailex.automatone.behavior.LookBehavior;
import me.sailex.automatone.behavior.MemoryBehavior;
import me.sailex.automatone.behavior.PathingBehavior;
import me.sailex.automatone.cache.WorldProvider;
import me.sailex.automatone.command.defaults.DefaultCommands;
import me.sailex.automatone.command.manager.BaritoneCommandManager;
import me.sailex.automatone.event.GameEventHandler;
import me.sailex.automatone.process.BackfillProcess;
import me.sailex.automatone.process.BuilderProcess;
import me.sailex.automatone.process.CustomGoalProcess;
import me.sailex.automatone.process.ExploreProcess;
import me.sailex.automatone.process.FarmProcess;
import me.sailex.automatone.process.FishingProcess;
import me.sailex.automatone.process.FollowProcess;
import me.sailex.automatone.process.GetToBlockProcess;
import me.sailex.automatone.process.MineProcess;
import me.sailex.automatone.selection.SelectionManager;
import me.sailex.automatone.settings.AltoClefSettings;
import me.sailex.automatone.utils.BlockStateInterface;
import me.sailex.automatone.utils.CarpetPlayerCommandHelper;
import me.sailex.automatone.utils.InputOverrideHandler;
import me.sailex.automatone.utils.PathingControlManager;
import me.sailex.automatone.utils.player.EntityContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * @author Brady
 * @since 7/31/2018
 */
public class Baritone implements IBaritone {

    private final Settings settings;
    private final GameEventHandler gameEventHandler;

    private final PathingBehavior pathingBehavior;
    private final LookBehavior lookBehavior;
    private final MemoryBehavior memoryBehavior;
    private final InventoryBehavior inventoryBehavior;
    private final InputOverrideHandler inputOverrideHandler;

    private final FollowProcess followProcess;
    private final MineProcess mineProcess;
    private final GetToBlockProcess getToBlockProcess;
    private final CustomGoalProcess customGoalProcess;
    private final BuilderProcess builderProcess;
    private final ExploreProcess exploreProcess;
    private final BackfillProcess backfillProcess;
    private final FarmProcess farmProcess;
    private final FishingProcess fishingProcess;
    private final IBaritoneProcess execControlProcess;

    private final PathingControlManager pathingControlManager;
    private final BaritoneCommandManager commandManager;

    private final IEntityContext playerContext;

    public BlockStateInterface bsi;

    private final CarpetPlayerCommandHelper commandHelper;

    private final WorldProvider worldProvider;
    private final ISelectionManager selectionManager;
    private final AltoClefSettings altoClefSettings;

    public Baritone(ServerPlayer player) {
        this.commandHelper = new CarpetPlayerCommandHelper(this, player);
        this.settings = new Settings();
        this.gameEventHandler = new GameEventHandler(this);

        // Define this before behaviors try and get it, or else it will be null and the builds will fail!
        this.playerContext = new EntityContext(player);

        {
            // the Behavior constructor calls baritone.registerBehavior(this) so this populates the behaviors arraylist
            pathingBehavior = new PathingBehavior(this);
            lookBehavior = new LookBehavior(this);
            memoryBehavior = new MemoryBehavior(this);
            inventoryBehavior = new InventoryBehavior(this);
            inputOverrideHandler = new InputOverrideHandler(this);
        }

        this.pathingControlManager = new PathingControlManager(this);
        {
            this.pathingControlManager.registerProcess(followProcess = new FollowProcess(this));
            this.pathingControlManager.registerProcess(mineProcess = new MineProcess(this));
            this.pathingControlManager.registerProcess(customGoalProcess = new CustomGoalProcess(this)); // very high iq
            this.pathingControlManager.registerProcess(getToBlockProcess = new GetToBlockProcess(this));
            this.pathingControlManager.registerProcess(builderProcess = new BuilderProcess(this));
            this.pathingControlManager.registerProcess(exploreProcess = new ExploreProcess(this));
            this.pathingControlManager.registerProcess(backfillProcess = new BackfillProcess(this));
            this.pathingControlManager.registerProcess(farmProcess = new FarmProcess(this));
            this.pathingControlManager.registerProcess(fishingProcess = new FishingProcess(this));
        }

        this.commandManager = new BaritoneCommandManager(this);
        this.execControlProcess = DefaultCommands.controlCommands.registerProcess(this);

        this.worldProvider = new WorldProvider(EntityVer.getWorld(player));
        this.selectionManager = new SelectionManager(player);
        this.altoClefSettings = new AltoClefSettings();
    }

    @Override
    public PathingControlManager getPathingControlManager() {
        return this.pathingControlManager;
    }

    public void registerBehavior(Behavior behavior) {
        this.gameEventHandler.registerEventListener(behavior);
    }

    @Override
    public InputOverrideHandler getInputOverrideHandler() {
        return this.inputOverrideHandler;
    }

    @Override
    public CustomGoalProcess getCustomGoalProcess() {
        return this.customGoalProcess;
    }

    @Override
    public GetToBlockProcess getGetToBlockProcess() {
        return this.getToBlockProcess;
    }

    @Override
    public IEntityContext getPlayerContext() {
        return this.playerContext;
    }

    public MemoryBehavior getMemoryBehavior() {
        return this.memoryBehavior;
    }

    @Override
    public FollowProcess getFollowProcess() {
        return this.followProcess;
    }

    @Override
    public BuilderProcess getBuilderProcess() {
        return this.builderProcess;
    }

    public InventoryBehavior getInventoryBehavior() {
        return this.inventoryBehavior;
    }

    @Override
    public LookBehavior getLookBehavior() {
        return this.lookBehavior;
    }

    public ExploreProcess getExploreProcess() {
        return this.exploreProcess;
    }

    @Override
    public MineProcess getMineProcess() {
        return this.mineProcess;
    }

    public FarmProcess getFarmProcess() {
        return this.farmProcess;
    }

    @Override
    public PathingBehavior getPathingBehavior() {
        return this.pathingBehavior;
    }

    @Override
    public WorldProvider getWorldProvider() {
        return this.worldProvider;
    }

    @Override
    public ISelectionManager getSelectionManager() {
        return this.selectionManager;
    }

    @Override
    public IEventBus getGameEventHandler() {
        return this.gameEventHandler;
    }

    @Override
    public BaritoneCommandManager getCommandManager() {
        return this.commandManager;
    }

    public IBaritoneProcess getExecControlProcess() {
        return execControlProcess;
    }

    @Override
    public boolean isActive() {
        return this.pathingControlManager.isActive();
    }

    public Settings settings() {
        return this.settings;
    }

    @Override
    public void logDebug(String message) {
        Automatone.LOGGER.debug(message);

        if (!BaritoneAPI.getGlobalSettings().chatDebug.get()) {
            return;
        }

        // We won't log debug chat into toasts
        // Because only a madman would want that extreme spam -_-
        logDirect(message);

        if (!this.settings.syncWithOps.get()) return;

        MinecraftServer server = this.getPlayerContext().world().getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp( p.getGameProfile() )) {
                logDirect(message);
            }
        }
    }

    @Override
    public void serverTick() {
        this.getGameEventHandler().onTickServer();
    }

    @Override
    public ICommandHelper getCommandHelper() {
        return this.commandHelper;
    }

    public AltoClefSettings getExtraBaritoneSettings() {
        return this.altoClefSettings;
    }

    public FishingProcess getFishingProcess() {
        return this.fishingProcess;
    }
}
