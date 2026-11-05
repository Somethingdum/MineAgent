// SMH plan 70: the companion's first days on its own, the AltoClef way. What it wants is a list of AltoClef's own
// priority tasks (adris/altoclef/tasks/speedrun/beatgame/prioritytask, MIT, Copyright (c) 2020 Adris Jautakas; see
// THIRD_PARTY_NOTICES.md), most taken as BeatMinecraftTask makes them (at commit 1f8e038): its pickaxes, stone tools, food
// (CollectFoodPriorityCalculator and CollectFoodTask), cooking, coal, and its bed at night (PlaceBedAndSetSpawnTask); and
// run as BeatMinecraftTask runs its list (PrioritySelector). Its owner's minimum by its third day is what it aims at: "a
// hut, a furnace, a crafting table, some food, some fuel. And at least stone tools", "a bed if possible", and else
// "waiting motionless until all of the mobs have burned". The hut is BuildHutTask (Mindcraft's dirt shelter on
// Baritone's builder). Charcoal when it has seen no coal, and the night in its hut, are written for this mod from those
// same pieces (SmeltInFurnaceTask, GetToBlockTask).
package com.mineagent.engine.alto.tasks.life;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasks.CraftInInventoryTask;
import com.mineagent.engine.alto.tasks.container.CraftInTableTask;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasks.construction.BuildHutTask;
import com.mineagent.engine.alto.tasks.container.SmeltInFurnaceTask;
import com.mineagent.engine.alto.tasks.misc.PlaceBedAndSetSpawnTask;
import com.mineagent.engine.alto.tasks.movement.GetToBlockTask;
import com.mineagent.engine.alto.tasks.resources.CollectFoodTask;
import com.mineagent.engine.alto.tasks.resources.CollectMeatTask;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.PrioritySelector;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.prioritycalculators.CollectFoodPriorityCalculator;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.prioritycalculators.DistanceItemPriorityCalculator;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.prioritycalculators.StaticItemPriorityCalculator;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.tasks.ActionPriorityTask;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.tasks.CraftItemPriorityTask;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.tasks.MineBlockPriorityTask;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.tasks.PriorityTask;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.tasks.RecraftableItemPriorityTask;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.tasks.ResourcePriorityTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.MiningRequirement;
import com.mineagent.engine.alto.util.RecipeTarget;
import com.mineagent.engine.alto.util.SmeltTarget;
import com.mineagent.engine.alto.util.helpers.ItemHelper;
import com.mineagent.engine.alto.util.helpers.StorageHelper;
import com.mineagent.engine.alto.util.helpers.WorldHelper;
import com.mineagent.engine.alto.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class OwnLifeTask extends Task {

    /** Its owner: "It just needs ENOUGH food to survive until morning": food enough for a night and the day after. */
    public static final double FOOD_UNITS = 30;
    /** Fuel: coal or charcoal enough to cook its food and smelt what its first days need. */
    public static final int FUEL = 8;
    /** Day time it heads home at, and time after dawn by which the mobs have burned (ticks of the day). */
    private static final int DUSK = 11_500, MOBS_BURNED = 1_000;

    private final List<PriorityTask> wants = new ArrayList<>();
    private final PrioritySelector selector = new PrioritySelector();
    private BlockPos hut;
    private BuildHutTask hutTask;

    /** Its life on its own, its hut at {@code hut} (or where it stands when it first builds one, if null). */
    public OwnLifeTask(BlockPos hut) {
        this.hut = hut == null ? null : hut.immutable();
    }

    /** Where its hut is (or will be), or null before it has chosen. */
    public BlockPos hut() {
        return hut;
    }

    private BuildHutTask hutTask(AltoClef mod) {
        if (hut == null) hut = mod.getPlayer().blockPosition().immutable();
        if (hutTask == null || !hutTask.getPlace().equals(hut)) hutTask = new BuildHutTask(hut);
        return hutTask;
    }

    private boolean hutStands(AltoClef mod) {
        return hut != null && BuildHutTask.shellStands(mod, hut);
    }

    /** Its hut stands and its door is in: a home for the night. */
    private boolean hutReady(AltoClef mod) {
        return hutStands(mod) && hutTask(mod).doorIn(mod);
    }

    private boolean inHut(AltoClef mod) {
        return hut != null && hutTask(mod).inside().closerToCenterThan(mod.getPlayer().position(), 1.6);
    }

    private boolean hutHas(AltoClef mod, net.minecraft.world.level.block.Block block) {
        if (hut == null) return false;
        for (BlockPos p : BlockPos.betweenClosed(hut.offset(-1, -3, -1), hut.offset(5, 1, 6))) {
            if (mod.getWorld().getBlockState(p).is(block)) return true;
        }
        return false;
    }

    private static int timeOfDay(AltoClef mod) {
        return (int) (mod.getWorld().getDayTime() % 24_000L);
    }

    private static boolean evening(AltoClef mod) {
        int t = timeOfDay(mod);
        return t >= DUSK || WorldHelper.canSleep();
    }

    /** Still before the mobs have burned: the night, and the start of the morning. */
    private static boolean mobsAbout(AltoClef mod) {
        int t = timeOfDay(mod);
        return evening(mod) || t < MOBS_BURNED;
    }

    private static boolean hasAny(AltoClef mod, Item... items) {
        return mod.getItemStorage().hasItem(items);
    }

    private static int fuel(AltoClef mod) {
        return mod.getItemStorage().getItemCount(Items.COAL, Items.CHARCOAL);
    }

    /** Its owner's minimum: stone tools, a crafting table and a furnace (with it or in its hut), fuel, food and a hut. */
    public boolean kitDone(AltoClef mod) {
        return StorageHelper.miningRequirementMet(MiningRequirement.STONE)
                && hasAny(mod, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE)
                && hasAny(mod, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD)
                && hasAny(mod, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL)
                && (hasAny(mod, Items.CRAFTING_TABLE) || hutHas(mod, Blocks.CRAFTING_TABLE))
                && (hasAny(mod, Items.FURNACE) || hutHas(mod, Blocks.FURNACE))
                && fuel(mod) >= FUEL
                && CollectFoodTask.calculateFoodPotential(mod) >= FOOD_UNITS
                && hutStands(mod);
    }

    // As BeatMinecraftTask.getRecipeTarget.
    private static RecipeTarget recipe(Item item) {
        ResourceTask task = TaskCatalogue.getItemTask(item, 1);
        if (task instanceof CraftInTableTask table) return table.getRecipeTargets()[0];
        if (task instanceof CraftInInventoryTask inventory) return inventory.getRecipeTarget();
        throw new IllegalStateException("Item isn't cataloged");
    }

    @Override
    protected void onStart() {
        AltoClef mod = AltoClef.getInstance();
        if (!wants.isEmpty()) return;

        // Its pickaxes, as BeatMinecraftTask.addPickaxeTasks: a wooden one when it has none, then a stone one.
        wants.add(new ResourcePriorityTask(StaticItemPriorityCalculator.of(400),
                a -> !hasAny(a, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE),
                ItemTarget.of(Items.WOODEN_PICKAXE)));
        wants.add(new RecraftableItemPriorityTask(410, 10_000, recipe(Items.STONE_PICKAXE),
                a -> StorageHelper.miningRequirementMet(MiningRequirement.WOOD)
                        && !hasAny(a, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)));
        // A crafting table of its own (for its hut), once it has its pickaxe.
        wants.add(new ResourcePriorityTask(StaticItemPriorityCalculator.of(380),
                a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE) && !hasAny(a, Items.CRAFTING_TABLE)
                        && !hutHas(a, Blocks.CRAFTING_TABLE),
                ItemTarget.of(Items.CRAFTING_TABLE)));
        // The rest of its stone tools, as BeatMinecraftTask.addStoneToolsTasks gets them (a ResourcePriorityTask: again
        // whenever one is missing, as a worn-out tool is; a CraftItemPriorityTask is done once only), its shovel too
        // ("at least stone tools").
        wants.add(new ResourcePriorityTask(StaticItemPriorityCalculator.of(300),
                a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE) && !hasAny(a, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD),
                ItemTarget.of(Items.STONE_SWORD)));
        wants.add(new ResourcePriorityTask(StaticItemPriorityCalculator.of(300),
                a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE) && !hasAny(a, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE),
                ItemTarget.of(Items.STONE_AXE)));
        wants.add(new ResourcePriorityTask(StaticItemPriorityCalculator.of(290),
                a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE) && !hasAny(a, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL),
                ItemTarget.of(Items.STONE_SHOVEL)));
        // Its hut: after its pickaxe and table, then its fittings (the table and furnace it carries put in); its walls,
        // roof and door before anything else once the day turns to evening.
        wants.add(new ActionPriorityTask(a -> {
            BuildHutTask t = hutTask(a);
            boolean shell = hutStands(a);
            if (shell && t.isFinished()) return new Pair<>(null, Double.NEGATIVE_INFINITY);
            if (evening(a)) {
                return !shell || !t.doorIn(a) ? new Pair<>(t, 900_000d) : new Pair<>(null, Double.NEGATIVE_INFINITY);
            }
            return new Pair<>(t, shell ? 330d : 360d);
        }, a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE) || evening(a), false, true, false));
        // A furnace.
        wants.add(new ResourcePriorityTask(StaticItemPriorityCalculator.of(340),
                a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE) && !hasAny(a, Items.FURNACE)
                        && !hutHas(a, Blocks.FURNACE),
                ItemTarget.of(Items.FURNACE)));
        // Fuel: coal it can see, as BeatMinecraftTask's ore tasks; else charcoal from logs in its furnace.
        wants.add(new MineBlockPriorityTask(ItemHelper.itemsToBlocks(new Item[]{Items.COAL_ORE, Items.DEEPSLATE_COAL_ORE}),
                new Item[]{Items.COAL}, MiningRequirement.WOOD, new DistanceItemPriorityCalculator(1050, 250, 5, FUEL, FUEL + 8),
                a -> fuel(a) < FUEL));
        wants.add(new ActionPriorityTask(a -> {
            int want = FUEL - fuel(a);
            if (want <= 0) return new Pair<>(null, Double.NEGATIVE_INFINITY);
            Task smelt = new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(Items.CHARCOAL, a.getItemStorage().getItemCount(Items.CHARCOAL) + want),
                    new ItemTarget("log", want)));
            return new Pair<>(smelt, 120d);
        }, a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE) && fuel(a) < FUEL, true, false, false));
        // Food, as BeatMinecraftTask.addCollectFoodTask (enough for its night), and cooked, as its addCookFoodTasks.
        List<Item> food = new LinkedList<>(ItemHelper.cookableFoodMap.values());
        food.addAll(ItemHelper.cookableFoodMap.keySet());
        food.addAll(List.of(Items.WHEAT, Items.BREAD));
        wants.add(new ResourcePriorityTask(new CollectFoodPriorityCalculator(mod, FOOD_UNITS),
                a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE)
                        && CollectFoodTask.calculateFoodPotential(a) < FOOD_UNITS,
                new CollectFoodTask(FOOD_UNITS), ItemTarget.of(food.toArray(new Item[]{}))));
        wants.add(new ActionPriorityTask(a -> {
            Pair<Task, Double> pair = new Pair<>(null, Double.NEGATIVE_INFINITY);
            int rawFoodCount = a.getItemStorage().getItemCount(ItemHelper.RAW_FOODS);
            double priority = rawFoodCount >= 8 ? 450 : rawFoodCount * 25;
            for (CollectMeatTask.CookableFoodTarget cookable : CollectMeatTask.COOKABLE_FOODS) {
                int rawCount = a.getItemStorage().getItemCount(cookable.getRaw());
                if (rawCount == 0) continue;
                int toSmelt = rawCount + a.getItemStorage().getItemCount(cookable.getCooked());
                pair.setLeft(new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(cookable.cookedFood, toSmelt), new ItemTarget(cookable.rawFood, rawCount))));
                pair.setRight(priority);
                return pair;
            }
            return pair;
        }, a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE) && (hasAny(a, Items.FURNACE) || hutHas(a, Blocks.FURNACE)), true, false, false));
        // A bed if it can: wool it carries, or sheep it can see (AltoClef's bed task shears or kills them).
        wants.add(new ActionPriorityTask(a -> {
            if (hasAny(a, ItemHelper.BED)) return new Pair<>(null, Double.NEGATIVE_INFINITY);
            boolean wool = a.getItemStorage().getItemCount(ItemHelper.WOOL) >= 3;
            if (!wool && !a.getEntityTracker().entityFound(Sheep.class)) return new Pair<>(null, Double.NEGATIVE_INFINITY);
            return new Pair<>(TaskCatalogue.getItemTask("bed", 1), wool ? 320d : 250d);
        }, a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE) && hasAny(a, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD),
                false, true, false));
        // What the model proposed (Voyager's curriculum, ModelWants): above all but its pickaxes and the night, done by
        // AltoClef's task for it and judged by the game; one that gets nowhere is rested, and the model told it failed.
        wants.add(new ActionPriorityTask(a -> {
            Task t = modelTask(a);
            if (t == null) return new Pair<>(null, Double.NEGATIVE_INFINITY);
            return new Pair<>(t, MODEL_PRIORITY);
        }, a -> true, false, true, false));
        // The night: home to its hut before dark; there, its bed (as BeatMinecraftTask.addSleepTask), else it waits.
        wants.add(new ActionPriorityTask(a -> {
            if (!mobsAbout(a) || !hutReady(a) || inHut(a)) return new Pair<>(null, Double.NEGATIVE_INFINITY);
            return new Pair<>(new GetToBlockTask(hutTask(a).inside()), 1_000_000d);
        }, a -> true, false, false, true));
        wants.add(new ActionPriorityTask(a -> {
            if (!WorldHelper.canSleep() || !hutReady(a) || !inHut(a) || !hasAny(a, ItemHelper.BED)) return new Pair<>(null, Double.NEGATIVE_INFINITY);
            return new Pair<>(new PlaceBedAndSetSpawnTask().stayInBed(), 1_100_000d);
        }, a -> true, true, false, true));
        wants.add(new ActionPriorityTask(a -> {
            if (!mobsAbout(a) || !hutReady(a) || !inHut(a)) return new Pair<>(null, Double.NEGATIVE_INFINITY);
            if (WorldHelper.canSleep() && hasAny(a, ItemHelper.BED)) return new Pair<>(null, Double.NEGATIVE_INFINITY);
            return new Pair<>(new WaitInHutTask(), 1_050_000d);
        }, a -> true, false, true, true));
    }

    /** Where the model's proposal sits among its wants: above its tools, hut and food, below its pickaxes and the night. */
    static final double MODEL_PRIORITY = 395;
    private String modelWords;
    private Task modelTask;

    /** The task for the model's proposal now waiting, or null (none, unreadable, or done: then the model is told). */
    private Task modelTask(AltoClef mod) {
        java.util.UUID id = mod.getAgent().companionId();
        String words = ModelWants.proposed(id);
        if (words == null) {
            modelWords = null;
            modelTask = null;
            return null;
        }
        if (!words.equals(modelWords)) {
            modelWords = words;
            modelTask = ModelWants.taskFor(mod, ModelWants.read(words));
            if (modelTask == null) {
                mod.log("The model's task \"" + words + "\" is none I can do: told it so.");
                ModelWants.finish(id, words, false);
                return null;
            }
        }
        if (modelTask.isFinished()) {
            mod.log("Done what the model proposed: " + words);
            ModelWants.finish(id, words, true);
            modelWords = null;
            modelTask = null;
            return null;
        }
        return modelTask;
    }

    /** A want that has run this long with nothing gained is left alone for a while (Voyager's "failed tasks"). */
    private static final long NO_GAIN_TICKS = 6_000L, REST_TICKS = 2_400L;
    private PriorityTask watching;
    private long watchingSince;
    private int watchingItems = -1;

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        Task next = selector.choose(mod, wants);
        noGainCheck(mod);
        setDebugState(selector.debug());
        return next;
    }

    /**
     * SMH (after Voyager's curriculum, MIT, which keeps the tasks that failed and proposes others meanwhile): the want it
     * is on, watched by what it carries. Five minutes on one with nothing gained or spent, and it is rested for two while
     * the next best is done; the night's wants are never rested.
     */
    private void noGainCheck(AltoClef mod) {
        PriorityTask cur = selector.current();
        long now = mod.getWorld().getGameTime();
        int items = 0;
        for (var stack : mod.getPlayer().getInventory().items) items += stack.getCount();
        if (cur != watching) {
            watching = cur;
            watchingSince = now;
            watchingItems = items;
            return;
        }
        if (cur == null || now - watchingSince < NO_GAIN_TICKS) return;
        if (items == watchingItems && cur.calculatePriority(mod) < 900_000) {
            mod.log("Got nowhere on " + cur + " for five minutes: on to something else for a while.");
            selector.rest(cur, now + REST_TICKS);
            // The model's proposal that got nowhere: it is told, and proposes something else.
            if (modelWords != null && cur.calculatePriority(mod) == MODEL_PRIORITY) {
                ModelWants.finish(mod.getAgent().companionId(), modelWords, false);
                modelWords = null;
                modelTask = null;
            }
        }
        watchingSince = now;
        watchingItems = items;
    }

    @Override
    protected void onStop(Task interruptTask) {
        selector.reset();
    }

    /** Whether it is on the model's proposal now (for its debug line). */
    public String modelWords() {
        return modelWords;
    }

    /** Done for now: its owner's minimum is there, and the mobs of the night have burned. */
    @Override
    public boolean isFinished() {
        AltoClef mod = AltoClef.getInstance();
        return !mobsAbout(mod) && kitDone(mod);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof OwnLifeTask;
    }

    @Override
    protected String toDebugString() {
        return "Living on its own" + (hut == null ? "" : " (hut at " + hut.toShortString() + ")");
    }

    /**
     * Its owner: "waiting motionless until all of the mobs have burned". In its hut with its door shut it holds still, its
     * eyes on any mob that comes in reach (AltoClef's mob defence acts on those), until the morning has burned them.
     */
    private static class WaitInHutTask extends Task {
        @Override
        protected void onStart() {
            AltoClef.getInstance().getClientBaritone().getPathingBehavior().forceCancel();
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            int t = timeOfDay(mod);
            boolean near = mod.getEntityTracker().entityFound(e -> e.distanceToSqr(mod.getPlayer()) < 16, Monster.class);
            setDebugState("Waiting in its hut for the mobs to burn (" + t + (near ? ", a mob near" : "") + ")");
            return null;
        }

        @Override
        protected void onStop(Task interruptTask) {
        }

        @Override
        public boolean isFinished() {
            return !mobsAbout(AltoClef.getInstance());
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof WaitInHutTask;
        }

        @Override
        protected String toDebugString() {
            return "Waiting in its hut";
        }
    }
}
