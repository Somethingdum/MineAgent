package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.UUID;

/**
 * One who took from its chest told to give it back to its face (SMH plan 70, its owner: "the agent should confront the
 * other player and demand it's items back"): it walks up to them, says what they took, and waits half a minute for it,
 * picking up what they throw down (what they owe comes off as it is picked up, loop/AgentLoop#givenBack); square, or
 * the half minute up, it goes back to what it was doing. It is told again the next time they are seen, two minutes on.
 */
public class ConfrontTask extends CompanionTask<ConfrontTask.ConfrontRecord> {
    public static final class ConfrontRecord extends TaskRecord {
        public final String player;
        public final String name;

        public ConfrontRecord(String toolCallId, UUID player, String name) {
            super(toolCallId);
            this.player = player.toString();
            this.name = name;
        }
    }

    private enum Phase { GO, WAIT, DONE }

    private PlayerNav nav;
    private Phase phase = Phase.GO;
    private int ticks;
    private String outcome = "";

    public ConfrontTask(AgentPlayer player, ConfrontRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        var loop = TaskContext.agentLoop(player);
        try {
            ticks++;
            UUID id = UUID.fromString(record.player);
            if (loop == null || loop.debts().of(record.player).isEmpty()) return end(true, "square");
            ServerPlayer them = sp.getServer() == null ? null : sp.getServer().getPlayerList().getPlayer(id);
            if (them == null || them.level() != sp.level() || !them.isAlive()) return end(true, "they went");
            switch (phase) {
                case GO -> {
                    if (sp.distanceTo(them) <= 3.5) {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, them.getEyePosition());
                        loop.sayTo(them, com.mineagent.engine.loop.AgentLoop.demand(them.getGameProfile().getName(),
                                loop.debts().of(record.player).get().owed()));
                        phase = Phase.WAIT;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 600) return end(true, "I couldn't get to them");
                    if (ticks % 20 == 1 || !nav.isNavigating()) nav.navigateNear(them.getBlockX(), them.getBlockY(), them.getBlockZ(), 2);
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case WAIT -> {
                    if (ticks > 600) return end(true, "they didn't give it back yet");
                    // What they threw down for it: walked onto, as soon as it can be picked up.
                    ItemEntity near = null;
                    for (var e : sp.serverLevel().getEntitiesOfClass(ItemEntity.class, sp.getBoundingBox().inflate(8, 3, 8),
                            e -> e.isAlive() && e.getOwner() != null && e.getOwner().getUUID().equals(id))) {
                        if (near == null || e.distanceToSqr(sp) < near.distanceToSqr(sp)) near = e;
                    }
                    if (near != null) {
                        if (ticks % 10 == 1 || !nav.isNavigating()) nav.navigateNear(near.getBlockX(), near.getBlockY(), near.getBlockZ(), 0);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, them.getEyePosition());
                    return TaskState.RUNNING;
                }
                default -> {
                    return end(true, null);
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        phase = Phase.DONE;
        outcome = why == null ? "done" : why;
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
