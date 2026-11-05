package me.sailex.altoclef;

import me.sailex.altoclef.tasks.construction.PlaceBlockNearbyTask;
import me.sailex.altoclef.tasks.construction.PlaceStructureBlockTask;
import me.sailex.altoclef.tasks.construction.compound.ConstructIronGolemTask;
import me.sailex.altoclef.tasks.construction.compound.ConstructNetherPortalObsidianTask;
import me.sailex.altoclef.tasks.container.SmeltInFurnaceTask;
import me.sailex.altoclef.tasks.container.StoreInAnyContainerTask;
import me.sailex.altoclef.tasks.entity.KillEntityTask;
import me.sailex.altoclef.tasks.entity.ShootArrowSimpleProjectileTask;
import me.sailex.altoclef.tasks.examples.ExampleTask2;
import me.sailex.altoclef.tasks.misc.EquipArmorTask;
import me.sailex.altoclef.tasks.misc.PlaceBedAndSetSpawnTask;
import me.sailex.altoclef.tasks.misc.RavageDesertTemplesTask;
import me.sailex.altoclef.tasks.misc.RavageRuinedPortalsTask;
import me.sailex.altoclef.tasks.movement.EnterNetherPortalTask;
import me.sailex.altoclef.tasks.movement.GoToStrongholdPortalTask;
import me.sailex.altoclef.tasks.movement.LocateDesertTempleTask;
import me.sailex.altoclef.tasks.movement.PickupDroppedItemTask;
import me.sailex.altoclef.tasks.movement.ThrowEnderPearlSimpleProjectileTask;
import me.sailex.altoclef.tasks.resources.CollectBlazeRodsTask;
import me.sailex.altoclef.tasks.resources.CollectFlintTask;
import me.sailex.altoclef.tasks.resources.CollectFoodTask;
import me.sailex.altoclef.tasks.resources.TradeWithPiglinsTask;
import me.sailex.altoclef.tasks.speedrun.KillEnderDragonTask;
import me.sailex.altoclef.tasks.speedrun.KillEnderDragonWithBedsTask;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.Dimension;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.SmeltTarget;
import me.sailex.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

public class Playground {
   public static void IDLE_TEST_INIT_FUNCTION(AltoClefController mod) {
   }

   public static void IDLE_TEST_TICK_FUNCTION(AltoClefController mod) {
   }

   public static void TEMP_TEST_FUNCTION(AltoClefController mod, String arg) {
      Debug.logMessage("Running test...");
      switch (arg) {
         case "":
            Debug.logWarning("Please specify a test (ex. stacked, bed, terminate)");
            return;
         case "pickup":
            mod.runUserTask(new PickupDroppedItemTask(new ItemTarget(Items.RAW_IRON, 3), true));
            return;
         case "chunk":
            BlockPos p = new BlockPos(100000, 3, 100000);
            Debug.logMessage("LOADED? " + (!(mod.getWorld().getChunk(p) instanceof EmptyLevelChunk) ? 1 : 0));
            return;
         case "structure":
            mod.runUserTask(new PlaceStructureBlockTask(new BlockPos(10, 6, 10)));
            return;
         case "place":
            mod.runUserTask(new PlaceBlockNearbyTask(Blocks.CRAFTING_TABLE, Blocks.FURNACE));
            return;
         case "stacked":
            mod.runUserTask(new EquipArmorTask(Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS));
            return;
         case "stacked2":
            mod.runUserTask(new EquipArmorTask(Items.DIAMOND_CHESTPLATE));
            return;
         case "ravage":
            mod.runUserTask(new RavageRuinedPortalsTask());
            return;
         case "temples":
            mod.runUserTask(new RavageDesertTemplesTask());
            return;
         case "smelt":
            ItemTarget target = new ItemTarget("iron_ingot", 4);
            ItemTarget material = new ItemTarget("iron_ore", 4);
            mod.runUserTask(new SmeltInFurnaceTask(new SmeltTarget(target, material)));
            return;
         case "iron":
            mod.runUserTask(new ConstructIronGolemTask());
            return;
         case "avoid":
            mod.getBehaviour()
               .avoidBlockBreaking(
                  (Predicate<BlockPos>)(b -> -1000 < b.getX() && b.getX() < 1000 && -1000 < b.getY() && b.getY() < 1000 && -1000 < b.getZ() && b.getZ() < 1000)
               );
            Debug.logMessage("Testing avoid from -1000, -1000, -1000 to 1000, 1000, 1000");
            return;
         case "portal":
            mod.runUserTask(
               new EnterNetherPortalTask(
                  new ConstructNetherPortalObsidianTask(), WorldHelper.getCurrentDimension(mod) == Dimension.OVERWORLD ? Dimension.NETHER : Dimension.OVERWORLD
               )
            );
            return;
         case "kill":
            List<Zombie> zombs = mod.getEntityTracker().getTrackedEntities(Zombie.class);
            if (zombs.size() == 0) {
               Debug.logWarning("No zombs found.");
            } else {
               LivingEntity entity = (LivingEntity)zombs.get(0);
               mod.runUserTask(new KillEntityTask(entity));
            }

            return;
         case "craft":
            new Thread(() -> {
               for (int i = 3; i > 0; i--) {
                  Debug.logMessage(i + "...");
                  sleepSec(1.0);
               }

               Item[] c = new Item[]{Items.COBBLESTONE};
               Item[] s = new Item[]{Items.STICK};
               CraftingRecipe recipe = CraftingRecipe.newShapedRecipe("test pickaxe", new Item[][]{c, c, c, null, s, null, null, s, null}, 1);
            }).start();
            return;
         case "food":
            mod.runUserTask(new CollectFoodTask(20.0));
            return;
         case "temple":
            mod.runUserTask(new LocateDesertTempleTask());
            return;
         case "blaze":
            mod.runUserTask(new CollectBlazeRodsTask(7));
            return;
         case "flint":
            mod.runUserTask(new CollectFlintTask(5));
            return;
         case "unobtainable":
            String fname = "unobtainables.txt";

            try {
               int unobtainable = 0;
               int total = 0;
               File f = new File(fname);
               FileWriter fw = new FileWriter(f);

               for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                  Item item = (Item)BuiltInRegistries.ITEM.get(id);
                  if (!TaskCatalogue.isObtainable(item)) {
                     unobtainable++;
                     fw.write(item.getDescriptionId() + "\n");
                  }

                  total++;
               }

               fw.flush();
               fw.close();
               Debug.logMessage(unobtainable + " / " + unobtainable + " unobtainable items. Wrote a list of items to \"" + total + "\".");
            } catch (IOException var17) {
               Debug.logWarning(var17.toString());
            }

            return;
         case "piglin":
            mod.runUserTask(new TradeWithPiglinsTask(32, new ItemTarget(Items.ENDER_PEARL, 12)));
            return;
         case "stronghold":
            mod.runUserTask(new GoToStrongholdPortalTask(12));
            return;
         case "bed":
            mod.runUserTask(new PlaceBedAndSetSpawnTask());
            return;
         case "dragon":
            mod.runUserTask(new KillEnderDragonWithBedsTask());
            return;
         case "dragon-pearl":
            mod.runUserTask(new ThrowEnderPearlSimpleProjectileTask(new BlockPos(0, 60, 0)));
            return;
         case "dragon-old":
            mod.runUserTask(new KillEnderDragonTask());
            return;
         case "chest":
            mod.runUserTask(new StoreInAnyContainerTask(true, new ItemTarget(Items.DIAMOND, 3)));
            return;
         case "example":
            mod.runUserTask(new ExampleTask2());
            return;
         case "netherite":
            mod.runUserTask(
               TaskCatalogue.getSquashedItemTask(
                  new ItemTarget("netherite_pickaxe", 1),
                  new ItemTarget("netherite_sword", 1),
                  new ItemTarget("netherite_helmet", 1),
                  new ItemTarget("netherite_chestplate", 1),
                  new ItemTarget("netherite_leggings", 1),
                  new ItemTarget("netherite_boots", 1)
               )
            );
            return;
         case "arrow":
            List<Ghast> ghasts = mod.getEntityTracker().getTrackedEntities(Ghast.class);
            if (ghasts.size() == 0) {
               Debug.logWarning("No ghasts found.");
            } else {
               Ghast ghast = ghasts.get(0);
               mod.runUserTask(new ShootArrowSimpleProjectileTask(ghast));
            }

            return;
         default:
            mod.logWarning("Test not found: \"" + arg + "\".");
      }
   }

   private static void sleepSec(double seconds) {
      try {
         Thread.sleep((int)(1000.0 * seconds));
      } catch (InterruptedException var3) {
         var3.printStackTrace();
      }
   }
}
