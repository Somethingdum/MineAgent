// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/GetRidOfExtraWaterBucketTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasks.resources.CollectBucketLiquidTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.ItemTarget;
import net.minecraft.world.item.Items;

/**
 * use when bot has too many water buckets but you dont want to throw them out
 */
public class GetRidOfExtraWaterBucketTask extends Task {

    private boolean needsPickup = false;

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        if (mod.getItemStorage().getItemCount(Items.WATER_BUCKET) != 0 && !needsPickup) {
            return new InteractWithBlockTask(new ItemTarget(Items.WATER_BUCKET, 1),mod.getPlayer().blockPosition().below(), false);
        }

        needsPickup = true;
        if (mod.getItemStorage().getItemCount(Items.WATER_BUCKET) < 1) {
            return new CollectBucketLiquidTask.CollectWaterBucketTask(1);
        }

        return null;
    }

    @Override
    public boolean isFinished() {
        return AltoClef.getInstance().getItemStorage().getItemCount(Items.WATER_BUCKET) == 1 && needsPickup;
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GetRidOfExtraWaterBucketTask;
    }

    @Override
    protected String toDebugString() {
        return null;
    }
}
