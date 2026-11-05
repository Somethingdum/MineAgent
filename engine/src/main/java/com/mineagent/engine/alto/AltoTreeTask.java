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
    private int ticks;

    public AltoTreeTask(AgentPlayer player, ProductionTask.ProduceRecord record) {
        super(player, record);
        this.name = catalogueName(record.item);
    }

    /** Each companion's AltoClef task chain as it last ran (what a scene's failure says it was doing). */
    private static final java.util.Map<java.util.UUID, String> LAST_CHAIN = new java.util.concurrent.ConcurrentHashMap<>();

    /** The companion's AltoClef task chain as it last ran, or "". */
    public static String lastChain(java.util.UUID companionId) {
        return LAST_CHAIN.getOrDefault(companionId, "");
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
            root = host.within(() -> TaskCatalogue.getItemTask(name, target));
        }
        if (root == null) {
            outcome = "I don't know how to get " + name.replace('_', ' ') + ".";
        }
        ended = false;
        if (Boolean.getBoolean("mineagent.traceWork") && root != null) {
            final Task tree = root;
            System.out.println("[MineAgent] alto: " + name + " x" + target + " (" + startCount + " carried): " + host.within(tree::toString));
        }
    }

    @Override
    protected TaskState onTick() {
        if (root == null) return finish(TaskState.FAILED);
        ServerPlayer sp = sp();
        if (!sp.isAlive()) return TaskState.RUNNING;
        AltoClef host = AltoClef.of(player);
        host.tick(root);
        if (++ticks % 40 == 0) {
            // AltoClef's task chain as it ran this tick, outermost first, each with its own debug state.
            StringBuilder chain = new StringBuilder();
            java.util.List<Task> tasks = host.getUserTaskChain().getTasks();
            if (tasks.size() > 4) chain.append("(").append(tasks.size() - 4).append(" more) ");
            for (Task t : tasks.subList(Math.max(0, tasks.size() - 4), tasks.size())) {
                if (chain.length() > 0 && !chain.toString().endsWith(") ")) chain.append(" > ");
                chain.append(host.within(t::toString));
            }
            // What the body showed: the chain in control, the open menu, the cursor and the 2x2 grid, the clicks.
            StringBuilder grid = new StringBuilder();
            var made = sp.inventoryMenu.getSlot(0).getItem();
            grid.append(made.isEmpty() ? "-" : made.getCount() + " " + BuiltInRegistries.ITEM.getKey(made.getItem()).getPath()).append(" <= ");
            for (int i = 1; i <= 4; i++) {
                var st = sp.inventoryMenu.getSlot(i).getItem();
                grid.append(st.isEmpty() ? "-" : st.getCount() + " " + BuiltInRegistries.ITEM.getKey(st.getItem()).getPath()).append(i < 4 ? "," : "");
            }
            var carried = sp.containerMenu.getCarried();
            String body = " | " + host.getTaskRunner().statusReport + "; menu " + sp.containerMenu.getClass().getSimpleName()
                    + " " + sp.containerMenu.containerId + "; cursor " + (carried.isEmpty() ? "-" : carried.getCount() + " "
                    + BuiltInRegistries.ITEM.getKey(carried.getItem()).getPath()) + "; grid " + grid + "; free "
                    + sp.getInventory().getFreeSlot() + "; clicks " + host.getSlotHandler().clicksAsked + "/"
                    + host.getSlotHandler().clicksHeld + "/" + host.getSlotHandler().clicksSent + "/"
                    + host.getController().clicked + (host.getController().refused.isEmpty() ? "" : " (" + host.getController().refused + ")")
                    + (host.getPlayer() == sp ? "" : "; HOST BODY STALE");
            LAST_CHAIN.put(player.companionId(), "tick " + ticks + " at " + sp.blockPosition().toShortString() + body + " || " + chain);
            if (Boolean.getBoolean("mineagent.traceWork") && ticks % 200 == 0) {
                System.out.println("[MineAgent] alto: " + LAST_CHAIN.get(player.companionId()));
            }
        }
        int have = ProductionTask.count(sp, item);
        final Task tree = root;
        if (host.within(tree::isFinished) || have >= target) {
            int got = have - startCount;
            outcome = "Done: " + got + " " + name.replace('_', ' ') + (got == 1 ? "" : "s") + ".";
            return finish(TaskState.SUCCESS);
        }
        if (sp.level().getGameTime() > record.deadline()) {
            int got = Math.max(0, have - startCount);
            String doing = host.within(tree::toString);
            outcome = "I couldn't make any " + name.replace('_', ' ') + " in time: I got " + got + " of " + record.count
                    + " (" + doing + ").";
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

    /** How many more it has than when the order began. */
    public int got() {
        return item == null || startCount < 0 ? 0 : Math.max(0, ProductionTask.count(sp(), item) - startCount);
    }

    /** What it says of how the order went. */
    public String outcome() {
        return outcome;
    }

    /** What it is doing, for the order's progress (the deepest task's debug state, AltoClef's own words). */
    public String doing() {
        final Task tree = root;
        return tree == null ? "" : AltoClef.of(player).within(tree::toString);
    }
}
