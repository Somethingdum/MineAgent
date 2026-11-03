package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.pathing.execute.PlayerNav;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Items meant for someone who is AFK go in a chest by them (SMH plan 70, MA5.4): items dropped at an AFK player's feet
 * vanish after five minutes, before they're back to pick them up. It walks to the chest and, from a player's reach,
 * moves the items in as a hopper would (so a double chest or a full slot behaves as in the game), then remembers what
 * the chest holds; the journal notes what it stored, for the line it says when they're back.
 */
public class StoreItemsTask extends CompanionTask<StoreItemsTask.StoreRecord> {
    public static final class StoreRecord extends TaskRecord {
        public final String itemId;
        public final int count;
        public final int x, y, z;

        public StoreRecord(String toolCallId, String itemId, int count, int x, int y, int z) {
            super(toolCallId);
            this.itemId = itemId;
            this.count = Math.max(1, count);
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private PlayerNav nav;
    private volatile boolean reached;
    private volatile boolean failed;
    private int stored;
    private String outcome = "";

    public StoreItemsTask(AgentPlayer player, StoreRecord record) {
        super(player, record);
    }

    private BlockPos chest() {
        return new BlockPos(record.x, record.y, record.z);
    }

    @Override
    protected void onStart() {
        nav = new PlayerNav(player, TaskContext.navCaches(player));
        nav.setListener(new PlayerNav.NavListener() {
            @Override public void onGoalReached() { reached = true; }
            @Override public void onNavigationFailed(String reason) { failed = true; }
        });
        reached = false;
        failed = false;
        ServerPlayer sp = TaskContext.serverPlayer(player);
        if (!sp.canInteractWithBlock(chest(), 0.0)) nav.navigateNear(record.x, record.y, record.z, 2);
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        var level = sp.serverLevel();
        BlockPos pos = chest();
        Container container = level.isLoaded(pos) ? HopperBlockEntity.getContainerAt(level, pos) : null;
        if (container == null) {
            outcome = "the chest at " + where() + " is gone; I'm keeping the " + name() + " for you";
            return TaskState.FAILED;
        }
        if (sp.canInteractWithBlock(pos, 0.0)) return store(sp, container);
        if (failed || reached) {
            outcome = "I couldn't get to the chest at " + where() + "; I'm keeping the " + name() + " for you";
            return TaskState.FAILED;
        }
        nav.tick();
        return TaskState.RUNNING;
    }

    private TaskState store(ServerPlayer sp, Container container) {
        var loop = TaskContext.agentLoop(player);
        String dim = sp.serverLevel().dimension().location().toString();
        long tick = sp.level().getGameTime();
        sp.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(chest()));
        // What it holds first, so the journal notes only what it put in.
        if (loop != null) loop.noteChest(dim, record.x, record.y, record.z, contents(container), tick);
        var inv = sp.getInventory();
        int remaining = record.count;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(record.itemId)) continue;
            int take = Math.min(remaining, stack.getCount());
            ItemStack moving = stack.split(take);
            ItemStack left = HopperBlockEntity.addItem(null, container, moving, null);
            int moved = take - left.getCount();
            if (!left.isEmpty()) stack.grow(left.getCount());
            if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            remaining -= moved;
            stored += moved;
            if (!left.isEmpty()) break; // the chest is full
        }
        container.setChanged();
        sp.swing(InteractionHand.MAIN_HAND);
        TaskContext.syncInventory(sp);
        if (loop != null) loop.noteChest(dim, record.x, record.y, record.z, contents(container), tick);
        if (stored == 0) {
            outcome = "the chest at " + where() + " is full; I'm keeping the " + name() + " for you";
            return TaskState.FAILED;
        }
        outcome = "put " + stored + " " + name() + " in the chest at " + where()
                + (stored < record.count ? "; it's full, so I'm keeping the other " + (record.count - stored) : "");
        return TaskState.SUCCESS;
    }

    private static Map<String, Integer> contents(Container c) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty()) items.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return items;
    }

    private String where() {
        return record.x + ", " + record.y + ", " + record.z;
    }

    private String name() {
        String id = record.itemId;
        int colon = id.indexOf(':');
        return (colon >= 0 ? id.substring(colon + 1) : id).replace('_', ' ');
    }

    /** How many it put in (the game tests). */
    public int stored() {
        return stored;
    }

    @Override
    protected String successMessage() {
        return "{\"success\":true,\"stored\":" + stored + ",\"chest\":[" + where() + "],\"note\":\"" + outcome + "\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"stored\":" + stored + ",\"note\":\"" + (outcome.isEmpty() ? "not stored" : outcome) + "\"}";
    }

    @Override
    protected void onInterrupt() {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }
}
