// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/control/PlayerExtraController.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.control;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.eventbus.EventBus;
import com.mineagent.engine.alto.eventbus.events.BlockBreakingCancelEvent;
import com.mineagent.engine.alto.eventbus.events.BlockBreakingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

public class PlayerExtraController {

    private final AltoClef mod;
    private BlockPos blockBreakPos;
    private double blockBreakProgress;

    public PlayerExtraController(AltoClef mod) {
        this.mod = mod;

        EventBus.subscribe(BlockBreakingEvent.class, evt -> onBlockBreak(evt.blockPos, evt.progress));
        EventBus.subscribe(BlockBreakingCancelEvent.class, evt -> onBlockStopBreaking());
    }

    private void onBlockBreak(BlockPos pos, double progress) {
        blockBreakPos = pos;
        blockBreakProgress = progress;
    }

    private void onBlockStopBreaking() {
        blockBreakPos = null;
        blockBreakProgress = 0;
    }

    public BlockPos getBreakingBlockPos() {
        return blockBreakPos;
    }

    public boolean isBreakingBlock() {
        return blockBreakPos != null;
    }

    public double getBreakingBlockProgress() {
        return blockBreakProgress;
    }

    public boolean inRange(Entity entity) {
        return mod.getPlayer().closerThan(entity, mod.getModSettings().getEntityReachRange());
    }

    public void attack(Entity entity) {
        if (inRange(entity)) {
            mod.getController().attack(mod.getPlayer(), entity);
            mod.getPlayer().swing(InteractionHand.MAIN_HAND);
        }
    }
}
