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

        // Anvils, grindstones and bartering (SMH plan 70, MA11.2, MA11.1)
        CompanionTaskFactory.register(
                AnvilTask.AnvilRecord.class,
                AnvilTask::new
        );
        CompanionTaskFactory.register(
                BarterTask.BarterRecord.class,
                BarterTask::new
        );

        // A librarian rolled for a book, fixed and walled in (SMH plan 70, trading mastery)
        CompanionTaskFactory.register(
                LibrarianTask.RollRecord.class,
                LibrarianTask::new
        );

        // Putting things away by what each chest is for (SMH plan 70, MA9.2)
        CompanionTaskFactory.register(
                PutAwayTask.PutAwayRecord.class,
                PutAwayTask::new
        );

        // Junk thrown into lava or water (SMH plan 70: "manage its inventory intelligently")
        CompanionTaskFactory.register(
                DumpJunkTask.DumpRecord.class,
                DumpJunkTask::new
        );

        // Mobs hunted for levels once it is doing well (SMH plan 70: "It should start seeking beef with mobs. Of any kind.")
        CompanionTaskFactory.register(
                XpHuntTask.XpRecord.class,
                XpHuntTask::new
        );

        // The End portal opened, and gone through when asked (SMH plan 70)
        CompanionTaskFactory.register(
                EndPortalTask.PortalRecord.class,
                EndPortalTask::new
        );

        // The dragon, on its owner's word only (SMH plan 70)
        CompanionTaskFactory.register(
                DragonTask.DragonRecord.class,
                DragonTask::new
        );

        // A zombie villager cured (SMH plan 70: trading mastery)
        CompanionTaskFactory.register(
                CureTask.CureRecord.class,
                CureTask::new
        );

        // Diamond gear to netherite at a smithing table (SMH plan 70: god-tier gear)
        CompanionTaskFactory.register(
                SmithingTask.SmithRecord.class,
                SmithingTask::new
        );

        // Loot from the chests the world made (SMH plan 70: "collect the craziest loot")
        CompanionTaskFactory.register(
                LootTask.LootRecord.class,
                LootTask::new
        );

        // A slime farm worked (SMH plan 70: "highly favoring of slime")
        CompanionTaskFactory.register(
                SlimeFarmTask.SlimeRecord.class,
                SlimeFarmTask::new
        );

        // Farms made (SMH plan 70: "build a base and basic farms")
        CompanionTaskFactory.register(
                FarmTask.FarmRecord.class,
                FarmTask::new
        );

        // Holes near home filled (SMH plan 70, MA9.4)
        CompanionTaskFactory.register(
                FillHolesTask.FillRecord.class,
                FillHolesTask::new
        );

        // Finding the stronghold with eyes of ender (SMH plan 70, MA11.5)
        CompanionTaskFactory.register(
                EyeTask.EyeRecord.class,
                EyeTask::new
        );

        // Fishing (SMH plan 70, MA8.5)
        CompanionTaskFactory.register(
                FishTask.FishRecord.class,
                FishTask::new
        );

        // Breeding two grown animals by hand (SMH plan 70, MA8.5)
        CompanionTaskFactory.register(
                BreedTask.BreedRecord.class,
                BreedTask::new
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
