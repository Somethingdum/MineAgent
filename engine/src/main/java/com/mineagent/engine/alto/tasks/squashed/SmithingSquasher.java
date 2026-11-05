// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/squashed/SmithingSquasher.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.squashed;


import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasks.container.UpgradeInSmithingTableTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.helpers.StorageHelper;
import com.mineagent.engine.alto.util.slots.Slot;
import com.mineagent.engine.alto.util.slots.SmithingTableSlot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

public class SmithingSquasher extends TypeSquasher<UpgradeInSmithingTableTask> {

    @Override
    protected List<ResourceTask> getSquashed(List<UpgradeInSmithingTableTask> tasks) {
        // Group materials + tools together, then return a list of the same UpgradeInSmithing tasks
        List<ResourceTask> result = new ArrayList<>();
        List<ItemTarget> units = new ArrayList<>();
        for (UpgradeInSmithingTableTask task : tasks) {
            units.add(task.getMaterials());
            units.add(task.getTools());
        }
        result.add(new GetMaterialsTask(units.toArray(ItemTarget[]::new)));
        // Afterwards, perform the smithing.
        result.addAll(tasks);
        return result;
    }

    private static class GetMaterialsTask extends ResourceTask {

        public GetMaterialsTask(ItemTarget[] targets) {
            super(targets);
        }

        @Override
        protected boolean shouldAvoidPickingUp(AltoClef mod) {
            return false;
        }

        @Override
        protected void onResourceStart(AltoClef mod) {

        }

        private int getItemsInSlot(AltoClef mod, Slot slot, ItemTarget match) {
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (!stack.isEmpty() && match.matches(stack.getItem())) {
                return stack.getCount();
            }
            return 0;
        }

        @Override
        protected Task onResourceTick(AltoClef mod) {
            List<ItemTarget> resultingTargets = Arrays.asList(itemTargets);

            // Subtract required counts if we're in a smithing table, so putting items in the table doesn't remove them.
            boolean inSmithingTable = (mod.getPlayer().containerMenu instanceof SmithingMenu);
            if (inSmithingTable) {
                for (int i = 0; i < resultingTargets.size(); ++i) {
                    ItemTarget target = resultingTargets.get(i);
                    int smithingTableCount = getItemsInSlot(mod, SmithingTableSlot.INPUT_SLOT_MATERIALS, target)
                            + getItemsInSlot(mod, SmithingTableSlot.INPUT_SLOT_TOOL, target)
                            + getItemsInSlot(mod, SmithingTableSlot.OUTPUT_SLOT, target);
                    resultingTargets.set(i, new ItemTarget(target, target.getTargetCount() - smithingTableCount));
                }
            }
            return new CataloguedResourceTask(resultingTargets.toArray(ItemTarget[]::new));
        }

        @Override
        protected void onResourceStop(AltoClef mod, Task interruptTask) {

        }

        @Override
        protected boolean isEqualResource(ResourceTask other) {
            return true; // item targets are the only difference
        }

        @Override
        protected String toDebugStringName() {
            return "Collecting Smithing Materials";
        }
    }
}
