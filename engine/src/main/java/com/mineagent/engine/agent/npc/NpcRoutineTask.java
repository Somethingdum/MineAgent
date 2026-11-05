// Ported from Mindcraft (github.com/mindcraft-bots/mindcraft, MIT, Copyright (c) 2024 Kolby Nottingham) at e1c1fc3:
// src/agent/npc/controller.js (NPCContoller.executeNext and executeGoal), as one of AltoClef's tasks, re-decided every
// tick: by day its goals in order, the first one not yet met acted on (an item by AltoClef's catalogue in place of
// Mindcraft's item_goal; a blueprint by BuildGoalTask, the blocks it lacks fetched first); at night home (through its
// door, closed behind it) and to bed. Changes for the companion are marked SMH.
package com.mineagent.engine.agent.npc;

import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.tasks.InteractWithBlockTask;
import com.mineagent.sbe.altoclef.tasks.misc.PlaceBedAndSetSpawnTask;
import com.mineagent.sbe.altoclef.tasks.movement.GetToBlockTask;
import com.mineagent.sbe.altoclef.tasks.resources.CollectFoodTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class NpcRoutineTask extends Task {

    /** SMH: day time by which it is home for the night, less the walk (the mod's earlier rule: ShelterTask.DUSK). */
    static final int DUSK = 11_000, EARLIEST_HOME = 8_000;
    /** SMH (its owner: "waiting motionless until all of the mobs have burned"): stays in after dawn this long. */
    static final int MOBS_BURNED = 1_000;
    /** SMH: "food" as a goal is this many of AltoClef's food units (CollectFoodTask), a night's worth and more. */
    static final double FOOD_UNITS = 30;

    private final NpcData data;
    /** The blocks its builds lack, fetched first (Mindcraft's temp_goals; SMH: kept until met, not remade each call). */
    private final List<NpcData.Goal> tempGoals = new ArrayList<>();
    private final Map<String, BuildGoalTask> builds = new HashMap<>();
    /** It spent the night at home: in till the mobs have burned. */
    private boolean nightAtHome;
    /** Told when no goal is left to act on and it may set its own (Mindcraft's setGoal, the model's turn). */
    private final Consumer<NpcRoutineTask> wantsGoal;

    public NpcRoutineTask(NpcData data, Consumer<NpcRoutineTask> wantsGoal) {
        this.data = data;
        this.wantsGoal = wantsGoal;
    }

    public NpcData data() {
        return data;
    }

    @Override
    protected void onStart() {
        // SMH: what it has built is kept from being broken for blocks (the nearest dirt to a dirt hut is the hut), as
        // AltoClef keeps a portal's frame (ConstructNetherPortalObsidianTask): a block of a build standing as asked.
        controller.getBehaviour().push();
        controller.getBehaviour().avoidBlockBreaking(pos -> {
            for (BuildGoalTask b : builds.values()) if (b.standingPart(controller.getWorld(), pos)) return true;
            return false;
        });
    }

    @Override
    protected void onStop(Task interruptTask) {
        controller.getBehaviour().pop();
    }

    @Override
    protected Task onTick() {
        ServerLevel level = controller.getWorld();
        ServerPlayer player = controller.getPlayer();
        long t = level.getDayTime() % 24_000L;
        BuildGoalTask home = data.home == null ? null : build(data.home);
        boolean night = data.doRoutine && (t >= leaveAt(player, home) || nightAtHome && t < MOBS_BURNED);
        if (!night) {
            nightAtHome = false;
            return executeGoal();
        }
        // Mindcraft: at the end of the day the goal is reset, it returns home and goes to bed.
        data.currGoal = null;
        if (home == null) return executeGoal(); // SMH: no home yet; its home is its first build, so on with it
        if (!home.holds(player.blockPosition())) {
            setDebugState("Home for the night");
            return new GetToBlockTask(home.inside());
        }
        nightAtHome = true;
        // Mindcraft's useDoor closes the door behind it.
        BlockPos door = home.door(level);
        if (door != null) {
            BlockState d = level.getBlockState(door);
            if (d.getBlock() instanceof DoorBlock && d.getValue(DoorBlock.OPEN)) {
                setDebugState("Closing its door");
                return new InteractWithBlockTask(door);
            }
        }
        // Mindcraft's goToBed: the nearest bed; SMH: one in its home (a bed out in the dark is no night at home), else the
        // one it carries, put down in its home.
        if (bedInside(level, home) || carriesBed(player)) {
            setDebugState("To bed");
            return new PlaceBedAndSetSpawnTask().stayInBed();
        }
        // SMH (its owner: "If it cannot achieve a bed, waiting motionless until all of the mobs have burned is equally acceptable").
        setDebugState("Waiting in its home till the mobs have burned");
        return null;
    }

    /** Mindcraft's executeGoal: the first goal not yet met, acted on; the model asked for one when none is left. */
    private Task executeGoal() {
        ServerPlayer player = controller.getPlayer();
        data.idle = false;
        // The model's goal, met: told (the curriculum's verdict, from what the game shows), and cleared.
        if (data.currGoal != null && met(player, data.currGoal)) {
            NpcData.Goal done = data.currGoal;
            data.currGoal = null;
            if (data.onCurrGoalMet != null) data.onCurrGoalMet.accept(done);
        }
        tempGoals.removeIf(g -> met(player, g));
        List<NpcData.Goal> goals = new ArrayList<>(tempGoals);
        goals.addAll(data.goals);
        if (data.currGoal != null) goals.add(data.currGoal);
        for (NpcData.Goal goal : goals) {
            Blueprint bp = Blueprint.named(goal.name());
            if (bp == null) {
                if (met(player, goal)) continue;
                Task item = itemTask(goal);
                if (item == null) continue; // nothing in the catalogue gets it
                setDebugState("Goal: " + goal.quantity() + " " + goal.name());
                return item;
            }
            BuildGoalTask b = build(goal.name());
            b.controller = controller;
            Task step = b.plan();
            if (step != null) {
                setDebugState("Goal: the " + goal.name());
                return b;
            }
            if (b.built()) {
                boolean first = !data.built.containsKey(goal.name());
                data.built.putIfAbsent(goal.name(), new NpcData.Built(goal.name(), b.position(), b.orientation()));
                if (data.home == null) data.home = goal.name(); // SMH: its home once it stands (Mindcraft: once nothing is missing)
                if (first && data.onBuilt != null) data.onBuilt.accept(data.built.get(goal.name()));
                continue;
            }
            // Short of blocks: those first (Mindcraft's temp goals).
            for (var m : b.missing().entrySet()) {
                NpcData.Goal need = new NpcData.Goal(m.getKey(), m.getValue());
                if (tempGoals.stream().noneMatch(g -> g.name().equals(need.name()))) tempGoals.add(need);
            }
            for (NpcData.Goal need : tempGoals) {
                if (met(player, need)) continue;
                Task item = itemTask(need);
                if (item != null) {
                    setDebugState("For the " + goal.name() + ": " + need.quantity() + " " + need.name());
                    return item;
                }
            }
        }
        data.idle = true;
        if (data.doSetGoal && wantsGoal != null) wantsGoal.accept(this);
        setDebugState("Every goal met");
        return null;
    }

    private BuildGoalTask build(String name) {
        return builds.computeIfAbsent(name, n -> {
            NpcData.Built at = data.built.get(n);
            return new BuildGoalTask(Blueprint.named(n), at == null ? null : at.position(), at == null ? null : at.orientation());
        });
    }

    /** Mindcraft's itemSatisfied; SMH: "food" by its food units, "fuel" by coal and charcoal, a block it put in its home counted. */
    private boolean met(ServerPlayer player, NpcData.Goal goal) {
        switch (goal.name()) {
            case "food":
                return CollectFoodTask.calculateFoodPotential(controller) >= goal.quantity();
            case "fuel":
                return controller.getItemStorage().getItemCount(Items.COAL, Items.CHARCOAL) >= goal.quantity();
            default:
                if (goal.name().startsWith("kill:")) return kills(player, goal) >= goal.quantity();
                if (BuiltInRegistries.ITEM.getOptional(ResourceLocation.withDefaultNamespace(goal.name())).isEmpty()) {
                    // A catalogue name for a group of items ("log", "planks"): any of them.
                    Item[] matches = TaskCatalogue.getItemMatches(goal.name());
                    return matches != null && controller.getItemStorage().getItemCount(matches) >= goal.quantity();
                }
                if (NpcUtils.itemSatisfied(player, goal.name(), goal.quantity())) return true;
                return goal.quantity() == 1 && placedInHome(goal.name());
        }
    }

    /** Kills of the goal's mob since the goal was set, by the game's own statistics. */
    private int kills(ServerPlayer player, NpcData.Goal goal) {
        var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.withDefaultNamespace(goal.name().substring(5)));
        int now = player.getStats().getValue(net.minecraft.stats.Stats.ENTITY_KILLED.get(type));
        int start = data.killsAtStart.computeIfAbsent(goal.name(), k -> now);
        return now - start;
    }

    private boolean placedInHome(String name) {
        if (data.home == null) return false;
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(name));
        if (block == net.minecraft.world.level.block.Blocks.AIR) return false;
        BuildGoalTask home = build(data.home);
        BlockPos at = home.position();
        if (at == null) return false;
        Blueprint bp = home.goal();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(0, bp.offset(), 0), at.offset(bp.sizeX() - 1, bp.offset() + bp.sizeY() - 1, bp.sizeZ() - 1))) {
            if (controller.getWorld().getBlockState(p).is(block)) return true;
        }
        return false;
    }

    /** The task that gets an item goal: AltoClef's catalogue (in place of Mindcraft's item_goal). */
    private Task itemTask(NpcData.Goal goal) {
        switch (goal.name()) {
            case "food":
                return new CollectFoodTask(goal.quantity() > 1 ? goal.quantity() : FOOD_UNITS);
            case "fuel": {
                // Coal it knows of, else charcoal from logs (AltoClef's catalogue knows both).
                if (controller.getBlockScanner().anyFound(net.minecraft.world.level.block.Blocks.COAL_ORE,
                        net.minecraft.world.level.block.Blocks.DEEPSLATE_COAL_ORE)) {
                    return TaskCatalogue.getItemTask("coal", goal.quantity());
                }
                return TaskCatalogue.getItemTask("charcoal", goal.quantity());
            }
            default:
                if (goal.name().startsWith("kill:")) {
                    var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.withDefaultNamespace(goal.name().substring(5)));
                    return new com.mineagent.sbe.altoclef.tasks.entity.KillEntitiesTask(e -> e.getType() == type);
                }
                if (!TaskCatalogue.taskExists(goal.name())) return null;
                return TaskCatalogue.getItemTask(goal.name(), goal.quantity());
        }
    }

    /** SMH: by dusk, less the walk home (about 6.5 ticks a block), not before EARLIEST_HOME; no home: Mindcraft's 13000. */
    private static long leaveAt(ServerPlayer player, BuildGoalTask home) {
        if (home == null || home.inside() == null) return 13_000L;
        double dist = Math.sqrt(home.inside().distSqr(player.blockPosition()));
        return Math.max(EARLIEST_HOME, DUSK - (long) (dist * 6.5));
    }

    private static boolean carriesBed(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof BedItem) return true;
        return false;
    }

    private static boolean bedInside(ServerLevel level, BuildGoalTask home) {
        BlockPos at = home.position();
        if (at == null) return false;
        Blueprint bp = home.goal();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(0, bp.offset(), 0), at.offset(bp.sizeX() - 1, bp.offset() + bp.sizeY() - 1, bp.sizeZ() - 1))) {
            if (level.getBlockState(p).getBlock() instanceof BedBlock) return true;
        }
        return false;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof NpcRoutineTask r && r.data == data;
    }

    @Override
    protected String toDebugString() {
        return "Its daily routine";
    }
}
