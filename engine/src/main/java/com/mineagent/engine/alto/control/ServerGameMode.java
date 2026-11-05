package com.mineagent.engine.alto.control;

import com.mineagent.engine.alto.eventbus.EventBus;
import com.mineagent.engine.alto.eventbus.events.BlockInteractEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The calls AltoClef's tasks make on the client's game mode ({@code MultiPlayerGameMode}), done for the companion's
 * server-side body the way the server handles a real player's packets for them (SMH plan 70: AltoClef's tasks on the
 * companion). A client sees SUCCESS where the server says CONSUME for the same action, so an action that went through is
 * SUCCESS here, as the tasks expect.
 */
public final class ServerGameMode {

    private static InteractionResult asClientSees(InteractionResult result) {
        return result.consumesAction() ? InteractionResult.SUCCESS : result;
    }

    /** A right click on an entity (ServerGamePacketListenerImpl.handleInteract, INTERACT). */
    public InteractionResult interact(ServerPlayer player, Entity entity, InteractionHand hand) {
        if (player == null || entity == null || !player.canInteractWithEntity(entity, 3.0)) return InteractionResult.FAIL;
        return asClientSees(player.interactOn(entity, hand));
    }

    /** A right click on a block (handleUseItemOn): the block's use, else the item's use on it. */
    public InteractionResult useItemOn(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        if (player == null || hit == null) return InteractionResult.FAIL;
        EventBus.publish(new BlockInteractEvent(hit));
        InteractionResult result = player.gameMode.useItemOn(player, player.serverLevel(), player.getItemInHand(hand), hand, hit);
        if (result.consumesAction()) player.swing(hand);
        return asClientSees(result);
    }

    /** A right click with nothing in reach to use it on (handleUseItem). */
    public InteractionResult useItem(ServerPlayer player, InteractionHand hand) {
        if (player == null) return InteractionResult.FAIL;
        return asClientSees(player.gameMode.useItem(player, player.serverLevel(), player.getItemInHand(hand), hand));
    }

    /** The right click let go while using an item (handlePlayerAction, RELEASE_USE_ITEM). */
    public void releaseUsingItem(ServerPlayer player) {
        if (player != null) player.releaseUsingItem();
    }

    /** A left click on an entity (handleInteract, ATTACK). */
    public void attack(ServerPlayer player, Entity entity) {
        if (player == null || entity == null || entity == player) return;
        if (!player.canInteractWithEntity(entity, 3.0)) return;
        player.attack(entity);
    }

    /** A recipe clicked in the recipe book (handlePlaceRecipe). */
    public void handlePlaceRecipe(int containerId, RecipeHolder<?> recipe, boolean shiftDown) {
        ServerPlayer player = com.mineagent.engine.alto.AltoClef.getInstance().getPlayer();
        if (player == null || recipe == null || player.isSpectator()) return;
        if (player.containerMenu.containerId != containerId || !(player.containerMenu instanceof RecipeBookMenu<?, ?> menu)) return;
        if (!player.containerMenu.stillValid(player)) return;
        menu.handlePlacement(shiftDown, recipe, player);
        player.containerMenu.broadcastChanges();
    }

    /** A click in an open container (handleContainerClick). */
    public void handleInventoryMouseClick(int containerId, int slot, int button, ClickType type, ServerPlayer player) {
        if (player == null || player.isSpectator()) return;
        var menu = player.containerMenu;
        if (menu.containerId != containerId || !menu.stillValid(player)) return;
        if (!menu.isValidSlotIndex(slot)) return;
        menu.clicked(slot, button, type, player);
        menu.broadcastChanges();
    }
}
