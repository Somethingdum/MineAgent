// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/main/java/baritone/utils/player/BaritonePlayerController.java
// at commit 11ea66c (branch 1.21.1). SMH: what the client's game mode does for a player, done as the server does it on
// a player's packets: breaking through the companion's own game mode (FakePlayerGameMode, which finishes a break when its
// progress is full, as a client's does), clicks in its open container, a right click on a block or in the air.
package com.mineagent.engine.baritone.utils.player;

import com.mineagent.engine.baritone.api.utils.IPlayerController;
import com.mineagent.engine.entity.fakeplayer.FakePlayerGameMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class BaritonePlayerController implements IPlayerController {

    private final BaritonePlayerContext ctx;
    /** The block being broken (the client's destroyBlockPos), kept until it breaks or the break is let go. */
    private BlockPos destroying;
    /** The client's isDestroying: Baritone sets it each tick from its own record of whether it is still hitting. */
    private boolean hitting;

    public BaritonePlayerController(BaritonePlayerContext ctx) {
        this.ctx = ctx;
    }

    private ServerPlayer player() {
        return ctx.player();
    }

    private boolean breaking() {
        if (player().gameMode instanceof FakePlayerGameMode fake) {
            BlockPos at = fake.automaticDestroyPos();
            if (at == null) destroying = null;
            return at != null;
        }
        return destroying != null;
    }

    @Override
    public void syncHeldItem() {
        // SMH: the server holds what it holds; there is nothing to send.
    }

    @Override
    public boolean hasBrokenBlock() {
        return !breaking();
    }

    /**
     * The client's continueDestroyBlock: the same block it is breaking goes on breaking (the game mode carries the break
     * on each tick, as a client's progress does); another block, or none being broken, is started.
     */
    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        if (breaking() && pos.equals(destroying)) {
            hitting = true;
            return true;
        }
        return clickBlock(pos, side);
    }

    @Override
    public void resetBlockRemoving() {
        hitting = false;
        if (destroying == null) return;
        if (player().gameMode instanceof FakePlayerGameMode fake) fake.abortCurrentDestroy();
        else player().gameMode.handleBlockBreakAction(destroying, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                Direction.UP, player().serverLevel().getMaxBuildHeight(), 0);
        destroying = null;
    }

    @Override
    public void windowClick(int windowId, int slotId, int mouseButton, ClickType type, Player player) {
        var menu = player().containerMenu;
        if (menu.containerId != windowId || !menu.stillValid(player()) || !menu.isValidSlotIndex(slotId)) return;
        menu.clicked(slotId, mouseButton, type, player());
        menu.broadcastChanges();
    }

    @Override
    public GameType getGameType() {
        return player().gameMode.getGameModeForPlayer();
    }

    @Override
    public InteractionResult processRightClickBlock(ServerPlayer player, Level world, InteractionHand hand, BlockHitResult result) {
        InteractionResult r = player.gameMode.useItemOn(player, player.serverLevel(), player.getItemInHand(hand), hand, result);
        return r.consumesAction() ? InteractionResult.SUCCESS : r;
    }

    @Override
    public InteractionResult processRightClick(ServerPlayer player, Level world, InteractionHand hand) {
        InteractionResult r = player.gameMode.useItem(player, player.serverLevel(), player.getItemInHand(hand), hand);
        return r.consumesAction() ? InteractionResult.SUCCESS : r;
    }

    @Override
    public boolean clickBlock(BlockPos loc, Direction face) {
        if (destroying != null && !destroying.equals(loc)) resetBlockRemoving();
        player().gameMode.handleBlockBreakAction(loc, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, face,
                player().serverLevel().getMaxBuildHeight(), 0);
        player().swing(InteractionHand.MAIN_HAND);
        destroying = player().serverLevel().getBlockState(loc).isAir() ? null : loc.immutable();
        hitting = destroying != null;
        return true;
    }

    /**
     * SMH: the client's isDestroying flag only. Baritone clears it at the end of every tick it hits (the client's mouse
     * handling would, it notes) and sets it back at the start of the next; the block being broken is kept meanwhile, as
     * the client's destroyBlockPos is, so a break goes on instead of starting over each tick.
     */
    @Override
    public void setHittingBlock(boolean hittingBlock) {
        hitting = hittingBlock;
    }

    @Override
    public double getBlockReachDistance() {
        return com.mineagent.engine.act.BlockTargeting.interactionReach(player());
    }
}
