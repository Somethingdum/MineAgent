// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/AltoClefController.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef;

import com.mineagent.sbe.altoclef.chains.FoodChain;
import com.mineagent.sbe.altoclef.chains.MLGBucketFallChain;
import com.mineagent.sbe.altoclef.chains.MobDefenseChain;
import com.mineagent.sbe.altoclef.chains.PlayerDefenseChain;
import com.mineagent.sbe.altoclef.chains.PlayerInteractionFixChain;
import com.mineagent.sbe.altoclef.chains.PreEquipItemChain;
import com.mineagent.sbe.altoclef.chains.UnstuckChain;
import com.mineagent.sbe.altoclef.chains.UserTaskChain;
import com.mineagent.sbe.altoclef.chains.WorldSurvivalChain;
import com.mineagent.sbe.altoclef.commands.BlockScanner;
import com.mineagent.sbe.altoclef.commandsystem.CommandExecutor;
import com.mineagent.sbe.altoclef.control.InputControls;
import com.mineagent.sbe.altoclef.control.PlayerExtraController;
import com.mineagent.sbe.altoclef.control.SlotHandler;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.tasksystem.TaskRunner;
import com.mineagent.sbe.altoclef.trackers.CraftingRecipeTracker;
import com.mineagent.sbe.altoclef.trackers.EntityStuckTracker;
import com.mineagent.sbe.altoclef.trackers.EntityTracker;
import com.mineagent.sbe.altoclef.trackers.MiscBlockTracker;
import com.mineagent.sbe.altoclef.trackers.SimpleChunkTracker;
import com.mineagent.sbe.altoclef.trackers.TrackerManager;
import com.mineagent.sbe.altoclef.trackers.UserBlockRangeTracker;
import com.mineagent.sbe.altoclef.trackers.storage.ContainerSubTracker;
import com.mineagent.sbe.altoclef.trackers.storage.ItemStorageTracker;
import com.mineagent.sbe.automatone.Baritone;
import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.utils.IEntityContext;
import com.mineagent.sbe.automatone.api.utils.InteractionController;
import com.mineagent.sbe.automatone.settings.AltoClefSettings;
import com.mineagent.sbe.common.ServerTickable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AltoClefController implements ServerTickable {

   private final IBaritone baritone;
   private final IEntityContext ctx;
   private final CommandExecutor commandExecutor;
   private final TaskRunner taskRunner;
   private final TrackerManager trackerManager;
   private final BotBehaviour botBehaviour;
   private final UserTaskChain userTaskChain;
   private final FoodChain foodChain;
   private final MobDefenseChain mobDefenseChain;
   private final MLGBucketFallChain mlgBucketChain;
   private final ItemStorageTracker storageTracker;
   private ContainerSubTracker containerSubTracker;
   private final EntityTracker entityTracker;
   private final BlockScanner blockScanner;
   private final SimpleChunkTracker chunkTracker;
   private final MiscBlockTracker miscBlockTracker;
   private final CraftingRecipeTracker craftingRecipeTracker;
   private final EntityStuckTracker entityStuckTracker;
   private final UserBlockRangeTracker userBlockRangeTracker;
   private final InputControls inputControls;
   private final SlotHandler slotHandler;
   private final PlayerExtraController extraController;
   private Settings settings;
   private boolean paused = false;
   private boolean isEnabled = true;
   private Task storedTask;
   private Player owner;
   private final CommandStatusLogger commandStatusLogger;

   public AltoClefController(IBaritone baritone) {
      this.baritone = baritone;
      this.ctx = baritone.getPlayerContext();
      this.commandExecutor = new CommandExecutor(this);
      this.taskRunner = new TaskRunner(this);
      this.trackerManager = new TrackerManager(this);
      this.userTaskChain = new UserTaskChain(this.taskRunner);
      this.mobDefenseChain = new MobDefenseChain(this.taskRunner);
      new PlayerInteractionFixChain(this.taskRunner);
      this.mlgBucketChain = new MLGBucketFallChain(this.taskRunner);
      new UnstuckChain(this.taskRunner);
      new PreEquipItemChain(this.taskRunner);
      new WorldSurvivalChain(this.taskRunner);
      this.foodChain = new FoodChain(this.taskRunner);
      new PlayerDefenseChain(this.taskRunner);
      this.storageTracker = new ItemStorageTracker(this, this.trackerManager, container -> this.containerSubTracker = container);
      this.entityTracker = new EntityTracker(this.trackerManager);
      this.blockScanner = new BlockScanner(this);
      this.chunkTracker = new SimpleChunkTracker(this);
      this.miscBlockTracker = new MiscBlockTracker(this);
      this.craftingRecipeTracker = new CraftingRecipeTracker(this.trackerManager);
      this.entityStuckTracker = new EntityStuckTracker(this.trackerManager);
      this.userBlockRangeTracker = new UserBlockRangeTracker(this.trackerManager);
      this.inputControls = new InputControls(this);
      this.slotHandler = new SlotHandler(this);
      this.extraController = new PlayerExtraController(this);
      this.initializeBaritoneSettings();
      this.botBehaviour = new BotBehaviour(this);
      this.commandStatusLogger = new CommandStatusLogger(this);
      this.initializeCommands();
      Settings.load(
              newSettings -> {
                 this.settings = newSettings;
                 List<Item> baritoneCanPlace = Arrays.stream(this.settings.getThrowawayItems(this, true)).toList();
                 this.getBaritoneSettings().acceptableThrowawayItems.get().addAll(baritoneCanPlace);
                 if ((!this.getUserTaskChain().isActive() || this.getUserTaskChain().isRunningIdleTask())
                         && this.getModSettings().shouldRunIdleCommandWhenNotActive()) {
                    this.getUserTaskChain().signalNextTaskToBeIdleTask();
                    this.getCommandExecutor().executeWithPrefix(this.getModSettings().getIdleCommand());
                 }

                 this.getExtraBaritoneSettings().avoidBlockBreak(this.userBlockRangeTracker::isNearUserTrackedBlock);
                 this.getExtraBaritoneSettings().avoidBlockPlace(this.entityStuckTracker::isBlockedByEntity);
              }
      );
      registerTickListener();
   }

   @Override
   public void onTick() {
      this.inputControls.onTickPre();
      this.storageTracker.setDirty();
      this.miscBlockTracker.tick();
      this.trackerManager.tick();
      this.blockScanner.tick();
      this.taskRunner.tick();
      this.inputControls.onTickPost();
      this.commandStatusLogger.tick();
      this.baritone.serverTick();
   }

   public void stop() {
       this.setIsEnabled(false);
      this.getUserTaskChain().cancel(this);
      if (this.taskRunner.getCurrentTaskChain() != null) {
         this.taskRunner.getCurrentTaskChain().stop();
      }

      this.getTaskRunner().disable();
      this.getBaritone().getPathingBehavior().forceCancel();
      this.getBaritone().getInputOverrideHandler().clearAllKeys();
   }

   private void initializeBaritoneSettings() {
      this.getExtraBaritoneSettings().canWalkOnEndPortal(false);
      this.getExtraBaritoneSettings().avoidBlockPlace(this.entityStuckTracker::isBlockedByEntity);
      this.getExtraBaritoneSettings().avoidBlockBreak(this.userBlockRangeTracker::isNearUserTrackedBlock);
      this.getBaritoneSettings().freeLook.set(false);
      this.getBaritoneSettings().overshootTraverse.set(true);
      this.getBaritoneSettings().allowOvershootDiagonalDescend.set(true);
      this.getBaritoneSettings().allowInventory.set(true);
      this.getBaritoneSettings().allowParkour.set(false);
      this.getBaritoneSettings().allowParkourAscend.set(false);
      this.getBaritoneSettings().allowParkourPlace.set(false);
      this.getBaritoneSettings().allowDiagonalDescend.set(false);
      this.getBaritoneSettings().allowDiagonalAscend.set(false);
      this.getBaritoneSettings().fadePath.set(true);
      this.getBaritoneSettings().mineScanDroppedItems.set(false);
      this.getBaritoneSettings().mineDropLoiterDurationMSThanksLouca.set(0L);
      this.getExtraBaritoneSettings().configurePlaceBucketButDontFall(true);
      this.getBaritoneSettings().randomLooking.set(0.0);
      this.getBaritoneSettings().randomLooking113.set(0.0);
      this.getBaritoneSettings().failureTimeoutMS.reset();
      this.getBaritoneSettings().planAheadFailureTimeoutMS.reset();
      this.getBaritoneSettings().movementTimeoutTicks.reset();
   }

   private void initializeCommands() {
      try {
         AltoClefCommands.init(this);
      } catch (Exception e) {
         this.log(e.getMessage());
      }
   }

   public void runUserTask(Task task, Runnable onFinish) {
      this.userTaskChain.runTask(this, task, onFinish);
   }

   public void runUserTask(Task task) {
      this.runUserTask(task, () -> {
      });
   }

   public void cancelUserTask() {
      this.userTaskChain.cancel(this);
   }

   public CommandExecutor getCommandExecutor() {
      return this.commandExecutor;
   }

   public ServerPlayer getEntity() {
      return this.ctx.entity();
   }

   public ServerLevel getWorld() {
      return this.ctx.world();
   }

   public InteractionController getInteractionManager() {
      return this.ctx.interactionController();
   }

   public IBaritone getBaritone() {
      return this.baritone;
   }

   public com.mineagent.sbe.automatone.api.Settings getBaritoneSettings() {
      return this.baritone.settings();
   }

   public AltoClefSettings getExtraBaritoneSettings() {
      return ((Baritone) this.baritone).getExtraBaritoneSettings();
   }

   public TaskRunner getTaskRunner() {
      return this.taskRunner;
   }

   public UserTaskChain getUserTaskChain() {
      return this.userTaskChain;
   }

   public BotBehaviour getBehaviour() {
      return this.botBehaviour;
   }

   public boolean isPaused() {
      return this.paused;
   }

   public void setPaused(boolean pausing) {
      this.paused = pausing;
   }

   public Task getStoredTask() {
      return this.storedTask;
   }

   public void setStoredTask(Task currentTask) {
      this.storedTask = currentTask;
   }

   public ItemStorageTracker getItemStorage() {
      return this.storageTracker;
   }

   public EntityTracker getEntityTracker() {
      return this.entityTracker;
   }

   public CraftingRecipeTracker getCraftingRecipeTracker() {
      return this.craftingRecipeTracker;
   }

   public BlockScanner getBlockScanner() {
      return this.blockScanner;
   }

   public SimpleChunkTracker getChunkTracker() {
      return this.chunkTracker;
   }

   public MiscBlockTracker getMiscBlockTracker() {
      return this.miscBlockTracker;
   }

   public Settings getModSettings() {
      return this.settings;
   }

   public FoodChain getFoodChain() {
      return this.foodChain;
   }

   public MobDefenseChain getMobDefenseChain() {
      return this.mobDefenseChain;
   }

   public MLGBucketFallChain getMLGBucketChain() {
      return this.mlgBucketChain;
   }

   public void log(String message) {
      Debug.logMessage(message);
   }

   public void logWarning(String message) {
      Debug.logWarning(message);
   }

   public static boolean inGame() {
      return true;
   }

   public ServerPlayer getPlayer() {
      return this.ctx.entity();
   }

   public InputControls getInputControls() {
      return this.inputControls;
   }

   public SlotHandler getSlotHandler() {
      return this.slotHandler;
   }

   public Inventory getInventory() {
      return this.getBaritone().getPlayerContext().inventory();
   }

   public PlayerExtraController getControllerExtras() {
      return this.extraController;
   }

   @Override
   public boolean isEnabled() {
      return this.isEnabled;
   }

   @Override
   public void setIsEnabled(boolean isEnabled) {
      this.isEnabled =  isEnabled;
      if (!isEnabled) {
         cancelUserTask();
         this.getTaskRunner().disable();
      }
   }

   public Player getOwner() {
      return this.owner;
   }

   public void setOwner(Player owner) {
      this.owner = owner;
      //update sys prompt i guess
   }

   public boolean isOwner(UUID playerToCheck) {
      return playerToCheck.equals(owner.getUUID());
   }

   public String getOwnerUsername() {
      if (getOwner() == null) {
         return "UNKNOWN OWNER";
      }
      return getOwner().getName().getString();
   }

   public Optional<ServerPlayer> getClosestPlayer() {
      return this.getWorld().players().stream().sorted((a, b) -> {
         float adist = a.distanceTo(this.getEntity());
         float bdist = b.distanceTo(this.getEntity());
         return Float.compare(adist, bdist);
      }).findFirst();
   }

   public CommandStatusLogger getCommandStatusLogger() {
      return commandStatusLogger;
   }
}
