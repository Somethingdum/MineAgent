package com.mineagent.engine.task;

import com.mineagent.api.task.CompanionTaskFactory;
import com.mineagent.tools.InteractAtTool;
import com.mineagent.tools.InteractEntityTool;
import com.mineagent.tools.LocateBiomeTool;
import com.mineagent.tools.LocateStructureTool;
import com.mineagent.tools.block.AutoMineTool;
import com.mineagent.tools.block.BuildTool;
import com.mineagent.tools.combat.MeleeAttackTool;
import com.mineagent.tools.combat.RangedAttackTool;
import com.mineagent.tools.inventory.CollectItemsTool;
import com.mineagent.tools.inventory.EatItemTool;
import com.mineagent.tools.inventory.UseItemTool;
import com.mineagent.tools.management.WaitForTool;
import com.mineagent.tools.movement.MoveToTool;

/**
 * Registers all CompanionTask executor types in the CompanionTaskFactory.
 * Called once during engine initialization to wire up every TaskRecord
 * type to its corresponding executor.
 *
 * <p>After calling {@link #registerAll()}, the factory will be able to
 * create a CompanionTask for any TaskRecord dispatched by the tools.
 */
public final class TaskRegistration {

    private TaskRegistration() {}

    /**
     * Register all task types. Must be called once during engine startup.
     */
    public static void registerAll() {
        // Movement
        CompanionTaskFactory.register(
                MoveToTool.MoveToTaskRecord.class,
                MoveToTask::new
        );

        // Looking for a place it has never seen (SMH plan 70, MA5.6)
        CompanionTaskFactory.register(
                ExploreTask.ExploreRecord.class,
                ExploreTask::new
        );

        // Items meant for an AFK player, into a chest by them (SMH plan 70, MA5.4)
        CompanionTaskFactory.register(
                StoreItemsTask.StoreRecord.class,
                StoreItemsTask::new
        );

        // Walled in while its owner is away, when asked (SMH plan 70, MA5.4)
        CompanionTaskFactory.register(
                ShelterTask.ShelterRecord.class,
                ShelterTask::new
        );

        // Any amount of an item, the most efficient way, start to finish (SMH plan 70, production)
        CompanionTaskFactory.register(
                ProductionTask.ProduceRecord.class,
                ProductionTask::new
        );

        // Lighting an area until no spot is dark (SMH plan 70, MA9.4)
        CompanionTaskFactory.register(
                LightTask.LightRecord.class,
                LightTask::new
        );

        // Building from a blueprint (SMH plan 70, MA9.1)
        CompanionTaskFactory.register(
                BlueprintTask.BlueprintRecord.class,
                BlueprintTask::new
        );

        // Handing its owner what they asked for (SMH plan 70, MA8.5)
        CompanionTaskFactory.register(
                GiveTask.GiveRecord.class,
                GiveTask::new
        );

        // Enchanting, trading and brewing (SMH plan 70, MA11.2 to MA11.4)
        CompanionTaskFactory.register(
                EnchantTask.EnchantRecord.class,
                EnchantTask::new
        );
        CompanionTaskFactory.register(
                TradeTask.TradeRecord.class,
                TradeTask::new
        );
        CompanionTaskFactory.register(
                BrewTask.BrewRecord.class,
                BrewTask::new
        );

        // A nether portal from the obsidian it carries (SMH plan 70, MA11.1)
        CompanionTaskFactory.register(
                PortalTask.PortalRecord.class,
                PortalTask::new
        );

        // Hunting a named player (SMH plan 70, MA10.7)
        CompanionTaskFactory.register(
                HuntTask.HuntRecord.class,
                HuntTask::new
        );

        // Its owner's things after they die, fetched back (SMH plan 70, MA10.8)
        CompanionTaskFactory.register(
                RecoverTask.RecoverRecord.class,
                RecoverTask::new
        );

        // Mining on its own: branch tunnels at the ore's height (SMH plan 70, MA7.8)
        CompanionTaskFactory.register(
                MiningTask.MineRecord.class,
                MiningTask::new
        );

        // Mining
        CompanionTaskFactory.register(
                AutoMineTool.MineBlockTaskRecord.class,
                MineBlockTask::new
        );

        // Building
        CompanionTaskFactory.register(
                BuildTool.BuildTaskRecord.class,
                BuildTask::new
        );

        // Melee combat
        CompanionTaskFactory.register(
                MeleeAttackTool.MeleeAttackTaskRecord.class,
                MeleeAttackTask::new
        );

        // Ranged combat
        CompanionTaskFactory.register(
                RangedAttackTool.RangedAttackTaskRecord.class,
                RangedAttackTask::new
        );

        // Item collection
        CompanionTaskFactory.register(
                CollectItemsTool.CollectItemsTaskRecord.class,
                CollectItemsTask::new
        );

        // Timed food use
        CompanionTaskFactory.register(
                EatItemTool.EatItemTaskRecord.class,
                EatItemTask::new
        );

        CompanionTaskFactory.register(
                UseItemTool.UseItemTaskRecord.class,
                UseItemTask::new
        );

        CompanionTaskFactory.register(
                WaitForTool.WaitForTaskRecord.class,
                WaitForTask::new
        );

        // Block interaction
        CompanionTaskFactory.register(
                InteractAtTool.InteractAtTaskRecord.class,
                InteractAtTask::new
        );

        // Entity interaction
        CompanionTaskFactory.register(
                InteractEntityTool.InteractEntityTaskRecord.class,
                InteractEntityTask::new
        );

        // Structure location
        CompanionTaskFactory.register(
                LocateStructureTool.LocateStructureTaskRecord.class,
                LocateStructureTask::new
        );

        // Biome location
        CompanionTaskFactory.register(
                LocateBiomeTool.LocateBiomeTaskRecord.class,
                LocateBiomeTask::new
        );
    }
}
