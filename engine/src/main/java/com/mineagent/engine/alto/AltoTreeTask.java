package com.mineagent.engine.alto;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.task.ProductionTask;
import com.mineagent.engine.task.TaskContext;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * An order for an item ("make me 2 stone pickaxes", "get me 16 coal") done by AltoClef's own task for that item, from
 * its catalogue (TaskCatalogue: mined, crafted, smelted, hunted, looted, whatever its recipe tree says), on the
 * companion's body through its AltoClef host (SMH plan 70: port the best existing code; AltoClef's ResourceTask decides
 * again every tick from what is there now, where the planner it replaces decided once from memory). Its replies are the
 * production task's ("Done: ...", "I couldn't make any ...: ..."), which the rest of the engine reads.
 */
public class AltoTreeTask extends CompanionTask<ProductionTask.ProduceRecord> {

    private final String name;
    private Item item;
    private int target;
    private Task root;
    private boolean ended;
    private int startCount = -1;
    private String outcome = "";

    public AltoTreeTask(AgentPlayer player, ProductionTask.ProduceRecord record) {
        super(player, record);
        this.name = catalogueName(record.item);
    }

    /** The catalogue's name for an item id ("minecraft:stone_pickaxe" is "stone_pickaxe"), or null for none. */
    public static String catalogueName(String itemId) {
        if (itemId == null) return null;
        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        return TaskCatalogue.taskExists(path) ? path : null;
    }

    /** Whether an order can be done this way: an item the catalogue knows, kept on it (not a chest to fill). */
    public static boolean takes(ProductionTask.ProduceRecord record) {
        return record != null && record.chest == null && !record.placeChest && record.count > 0
                && catalogueName(record.item) != null;
    }

    private ServerPlayer sp() {
        return TaskContext.serverPlayer(player);
    }

    @Override
    protected void onStart() {
        ServerPlayer sp = sp();
        if (item == null) {
            ResourceLocation id = ResourceLocation.tryParse(record.item.contains(":") ? record.item : "minecraft:" + record.item);
            item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        }
        AltoClef host = AltoClef.of(player);
        if (startCount < 0) {
            startCount = ProductionTask.count(sp, item);
            // AltoClef counts what it carries: the order is for that many more.
            target = startCount + record.count;
            record.extendDeadlineTo(Math.max(record.deadline(), sp.level().getGameTime() + 24_000L));
        }
        if (root == null) {
            root = TaskCatalogue.getItemTask(name, target);
        }
        if (root == null) {
            outcome = "I don't know how to get " + name.replace('_', ' ') + ".";
        }
        ended = false;
        if (Boolean.getBoolean("mineagent.traceWork")) {
            System.out.println("[MineAgent] alto: " + name + " x" + target + " (" + startCount + " carried): " + root);
        }
    }

    @Override
    protected TaskState onTick() {
        if (root == null) return finish(TaskState.FAILED);
        ServerPlayer sp = sp();
        if (!sp.isAlive()) return TaskState.RUNNING;
        AltoClef host = AltoClef.of(player);
        host.tick(root);
        int have = ProductionTask.count(sp, item);
        if (root.isFinished() || have >= target) {
            int got = have - startCount;
            outcome = "Done: " + got + " " + name.replace('_', ' ') + (got == 1 ? "" : "s") + ".";
            return finish(TaskState.SUCCESS);
        }
        if (sp.level().getGameTime() > record.deadline()) {
            int got = Math.max(0, have - startCount);
            outcome = "I couldn't make any " + name.replace('_', ' ') + " in time: I got " + got + " of " + record.count
                    + (root != null ? " (" + root + ")" : "") + ".";
            return finish(TaskState.FAILED);
        }
        return TaskState.RUNNING;
    }

    private TaskState finish(TaskState state) {
        ended = true;
        return state;
    }

    @Override
    protected void onInterrupt() {
        AltoClef host = AltoClef.of(player);
        if (ended) {
            host.stop(root, null);
            root = null;
        } else {
            // Another took the body (a fight, food, the night): the tree is paused, and picks up again on resume.
            host.stop(root, root);
        }
    }

    @Override
    protected String successMessage() {
        return outcome.isEmpty() ? "Done." : outcome;
    }

    @Override
    protected String failureMessage() {
        return outcome.isEmpty() ? "I couldn't make any " + (name == null ? record.item : name.replace('_', ' ')) + "." : outcome;
    }

    @Override
    protected String timeoutMessage() {
        return failureMessage();
    }

    /** What it is doing, for the order's progress (the deepest task's debug state, AltoClef's own words). */
    public String doing() {
        return root == null ? "" : root.toString();
    }
}
