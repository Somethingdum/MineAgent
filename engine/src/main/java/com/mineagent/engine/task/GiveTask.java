package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.entity.CompanionEntity;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.planning.production.GameKnowledge;
import com.mineagent.engine.planning.production.ProductionPlanner;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Give me 8 food", "hand me 16 torches" (SMH plan 70, MA8.5: "get food for both of us"): it walks to its owner,
 * faces them and hands the items over, as a player tosses them; for food, the best it has first (by what each feeds,
 * from the game). Never while they're away: dropped items vanish.
 */
public class GiveTask extends CompanionTask<GiveTask.GiveRecord> {
    public static final class GiveRecord extends TaskRecord {
        /** An item as the game names it, or {@link ProductionTask#FOOD}. */
        public final String item;
        public final int count;

        public GiveRecord(String toolCallId, String item, int count) {
            super(toolCallId);
            this.item = item;
            this.count = Math.max(1, count);
        }
    }

    private PlayerNav nav;
    private int ticks;
    private int repath;
    /** The nearest it has got to them, and when (its walk getting no closer). */
    private double closest = Double.MAX_VALUE;
    private int closerAt;
    private String outcome = "";

    public GiveTask(AgentPlayer player, GiveRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        repath = 0;
        closest = Double.MAX_VALUE;
        closerAt = ticks;
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerPlayer owner = ((CompanionEntity) player).serverPlayerOwner();
        var loop = TaskContext.agentLoop(player);
        if (owner == null || owner.serverLevel() != sp.serverLevel()) return end(false, "you're not here to take them");
        if (loop != null && loop.ownerAfk()) return end(false, "you're away, and what I hand you would vanish before you're back");
        if (++ticks > 1_200) return end(false, "I couldn't get to you");
        // Mindcraft's giveToPlayer (skills.js, MIT, Copyright (c) 2024 Kolby Nottingham): it goes to within 3 blocks and
        // tosses them there; where the walk gets no closer (a gap, a wall's edge), it tosses from where it got to, the
        // player in sight (Mindcraft tosses whether or not its walk arrived).
        double away = owner.distanceTo(sp);
        if (away < closest - 0.25) {
            closest = away;
            closerAt = ticks;
        }
        boolean near = away <= 3.0 || away <= 6.0 && ticks - closerAt > 100 && sp.hasLineOfSight(owner);
        if (!near) {
            if (repath-- <= 0) {
                nav.navigateNear(owner.getBlockX(), owner.getBlockY(), owner.getBlockZ(), 2);
                repath = 20;
            }
            nav.tick();
            return TaskState.RUNNING;
        }
        nav.cancel();
        TaskContext.inputDriver(player).clear();
        sp.lookAt(EntityAnchorArgument.Anchor.EYES, owner.getEyePosition());
        int left = record.count;
        Map<String, Integer> gave = new LinkedHashMap<>();
        var inv = sp.getInventory();
        for (String id : order(sp)) {
            for (int i = 0; i < Math.min(36, inv.getContainerSize()) && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(id)) continue;
                ItemStack out = inv.removeItem(i, Math.min(left, s.getCount()));
                left -= out.getCount();
                gave.merge(id, out.getCount(), Integer::sum);
                com.mineagent.engine.survival.Manners.handedOver(sp.drop(out, false, true), sp);
            }
            if (left <= 0) break;
        }
        TaskContext.syncInventory(sp);
        if (gave.isEmpty()) return end(false, "I have none to give you");
        List<String> parts = new ArrayList<>();
        gave.forEach((k, v) -> parts.add(v + " " + ProductionPlanner.plural(k, v)));
        if (loop != null) loop.say("Here: " + String.join(", ", parts) + ".");
        return end(true, String.join(", ", parts));
    }

    /** What it hands over, in order: for food, the best it has first; else the item. */
    private List<String> order(ServerPlayer sp) {
        if (!ProductionTask.FOOD.equals(record.item)) return List.of(record.item);
        return new ArrayList<>(GameKnowledge.foods().keySet());
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = why;
        if (!ok) {
            var loop = TaskContext.agentLoop(player);
            if (loop != null) loop.say("I can't hand it over: " + why + ".");
        }
        return ok ? TaskState.SUCCESS : TaskState.FAILED;
    }

    @Override
    protected String successMessage() {
        return "{\"success\":true,\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected void onInterrupt() {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }
}
