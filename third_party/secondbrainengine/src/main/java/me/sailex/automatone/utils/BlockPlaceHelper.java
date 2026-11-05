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

import me.sailex.altoclef.multiversion.ActionResultVer;
import me.sailex.automatone.api.BaritoneAPI;
import me.sailex.automatone.api.utils.IEntityContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class BlockPlaceHelper {

    private final IEntityContext ctx;
    private int rightClickTimer;

    BlockPlaceHelper(IEntityContext playerContext) {
        this.ctx = playerContext;
    }

    public void tick(boolean rightClickRequested) {
        if (rightClickTimer > 0) {
            rightClickTimer--;
            return;
        }
        HitResult mouseOver = ctx.objectMouseOver();
        boolean isRowingBoat = ctx.entity().getVehicle() != null && ctx.entity().getVehicle() instanceof Boat;
        if (!rightClickRequested  || !(ctx.entity() instanceof Player) || isRowingBoat || mouseOver == null || mouseOver.getType() != HitResult.Type.BLOCK) {
            return;
        }

        rightClickTimer = BaritoneAPI.getGlobalSettings().rightClickSpeed.get();
        Player player = ctx.entity();

        for (InteractionHand hand : InteractionHand.values()) {
            InteractionResult actionResult = ctx.interactionController().processRightClickBlock(player, ctx.world(), hand, (BlockHitResult) mouseOver);
            if (actionResult.consumesAction()) {
                if (ActionResultVer.shouldSwingHand(actionResult)) {
                    player.swing(hand);
                }
                return;
            }
            if (!player.getItemInHand(hand).isEmpty() && ctx.interactionController().processRightClick(player, ctx.world(), hand).consumesAction()) {
                return;
            }
        }
    }
}
