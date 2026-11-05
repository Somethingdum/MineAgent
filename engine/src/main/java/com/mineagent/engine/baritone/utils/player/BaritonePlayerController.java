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
    private BlockPos destroying;

    public BaritonePlayerController(BaritonePlayerContext ctx) {
        this.ctx = ctx;
    }

    private ServerPlayer player() {
        return ctx.player();
    }

    private boolean breaking() {
        return player().gameMode instanceof FakePlayerGameMode fake ? fake.automaticDestroyPos() != null : destroying != null;
    }

    @Override
    public void syncHeldItem() {
        // SMH: the server holds what it holds; there is nothing to send.
    }

    @Override
    public boolean hasBrokenBlock() {
        return !breaking();
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        if (!pos.equals(destroying) || !breaking()) {
            return clickBlock(pos, side);
        }
        return true;
    }

    @Override
    public void resetBlockRemoving() {
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
        return true;
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
        if (!hittingBlock) destroying = null;
    }

    @Override
    public double getBlockReachDistance() {
        return com.mineagent.engine.act.BlockTargeting.interactionReach(player());
    }
}
