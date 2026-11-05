// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/CollectMilkTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasks.entity.AbstractDoToEntityTask;
import com.mineagent.engine.alto.tasksystem.Task;
import java.util.Optional;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Items;

public class CollectMilkTask extends ResourceTask {

    private final int count;

    public CollectMilkTask(int targetCount) {
        super(Items.MILK_BUCKET, targetCount);
        count = targetCount;
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        // Make sure we have a bucket.
        if (!mod.getItemStorage().hasItem(Items.BUCKET)) {
            return TaskCatalogue.getItemTask(Items.BUCKET, 1);
        }
        // Dimension
        if (!mod.getEntityTracker().entityFound(Cow.class) && isInWrongDimension(mod)) {
            return getToCorrectDimensionTask(mod);
        }
        return new MilkCowTask();
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectMilkTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + count + " milk buckets.";
    }

    static class MilkCowTask extends AbstractDoToEntityTask {

        public MilkCowTask() {
            super(0, -1, -1);
        }

        @Override
        protected boolean isSubEqual(AbstractDoToEntityTask other) {
            return other instanceof MilkCowTask;
        }

        @Override
        protected Task onEntityInteract(AltoClef mod, Entity entity) {
            if (!mod.getItemStorage().hasItem(Items.BUCKET)) {
                Debug.logWarning("Failed to milk cow because you have no bucket.");
                return null;
            }
            if (mod.getSlotHandler().forceEquipItem(Items.BUCKET)) {
                mod.getController().interact(mod.getPlayer(), entity, InteractionHand.MAIN_HAND);
            }


            return null;
        }

        @Override
        protected Optional<Entity> getEntityTarget(AltoClef mod) {
            return mod.getEntityTracker().getClosestEntity(mod.getPlayer().position(), Cow.class);
        }

        @Override
        protected String toDebugString() {
            return "Milking Cow";
        }
    }
}
