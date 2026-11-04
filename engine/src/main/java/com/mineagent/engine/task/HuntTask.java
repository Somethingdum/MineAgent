package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.survival.GearUp;
import com.mineagent.engine.survival.MobDefenseChain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * Hunting a named player (SMH plan 70, MA10.7): geared up first (its best armour, weapon and shield on), it goes where
 * it last saw them, or to where the server says they are when that is allowed ({@code behaviour.liveTracking}); once it
 * sees them the fighting chain takes the fight ({@code survival/PlayerFightChain}, they are on its target list). When
 * they die it picks up what they dropped for its owner ({@code behaviour.huntDrops}) and reports. Once: "keep at it"
 * makes the order stand. "Stop" ends it at once, and takes them off the list.
 */
public class HuntTask extends CompanionTask<HuntTask.HuntRecord> {
    public static final class HuntRecord extends TaskRecord {
        public final String name;
        public final String uuid;

        public HuntRecord(String toolCallId, String name, UUID uuid) {
            super(toolCallId);
            this.name = name;
            this.uuid = uuid.toString();
        }
    }

    private enum Phase { GEAR, FIND, CLOSE, DROPS, DONE, FAILED }

    private Phase phase = Phase.GEAR;
    private PlayerNav nav;
    private BlockPos goingTo;
    private int ticks, repath;
    /** The body it is hunting, as it first saw it: a new one under the same name means they died and came back. */
    private ServerPlayer body;
    private BlockPos diedAt;
    private String outcome = "";

    public HuntTask(AgentPlayer player, HuntRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        goingTo = null;
        record.extendDeadlineTo(Math.max(record.deadline(), TaskContext.serverPlayer(player).level().getGameTime() + 36_000L));
    }

    private ServerPlayer target(ServerPlayer sp) {
        return sp.getServer() == null ? null : sp.getServer().getPlayerList().getPlayer(UUID.fromString(record.uuid));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ticks++;
        switch (phase) {
            case GEAR -> {
                GearUp.run(sp);
                MobDefenseChain.equipWeapon(sp);
                if (!(sp.getOffhandItem().getItem() instanceof ShieldItem) && sp.getOffhandItem().isEmpty()) {
                    TaskContext.selectInventoryItemForHand(player, "minecraft:shield", InteractionHand.OFF_HAND);
                }
                phase = Phase.FIND;
                ticks = 0;
                return TaskState.RUNNING;
            }
            case FIND -> {
                ServerPlayer t = target(sp);
                if (t != null && body == null && t.isAlive()) body = t;
                if (died(t)) return dropsNext(sp);
                if (t != null && t.serverLevel() == sp.serverLevel() && t.isAlive()
                        && (t.distanceTo(sp) <= 12 || t.distanceTo(sp) <= 64 && sp.hasLineOfSight(t))) {
                    phase = Phase.CLOSE;
                    return TaskState.RUNNING;
                }
                BlockPos where = null;
                if (t != null && t.serverLevel() == sp.serverLevel() && com.mineagent.engine.settings.Switches.liveTracking()) {
                    where = t.blockPosition();
                } else {
                    var loop = TaskContext.agentLoop(player);
                    var seen = loop == null ? null : loop.lastSeen(UUID.fromString(record.uuid)).orElse(null);
                    String dim = sp.serverLevel().dimension().location().toString();
                    if (seen != null && seen.dim().equals(dim)) where = seen.at();
                }
                if (where == null) return end(false, "I don't know where " + record.name + " is");
                if (sp.blockPosition().distSqr(where) <= 9) return end(false, record.name + " isn't where I last saw them");
                if (ticks > 6_000) return end(false, "I couldn't get to where I last saw " + record.name);
                go(sp, where, 2);
                return TaskState.RUNNING;
            }
            case CLOSE -> {
                ServerPlayer t = target(sp);
                if (died(t)) return dropsNext(sp);
                if (t == null) return end(false, record.name + " left the game");
                if (t.serverLevel() != sp.serverLevel() || t.distanceTo(sp) > 64) {
                    phase = Phase.FIND;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                // Closer; the fighting chain takes over in range.
                go(sp, t.blockPosition(), 2);
                return TaskState.RUNNING;
            }
            case DROPS -> {
                ItemEntity near = null;
                for (ItemEntity e : sp.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(diedAt).inflate(6, 3, 6),
                        x -> x.isAlive() && com.mineagent.engine.survival.Manners.mayTake(x, sp))) {
                    if (near == null || e.distanceToSqr(sp) < near.distanceToSqr(sp)) near = e;
                }
                if (near == null || ticks > 400) {
                    return end(true, record.name + " is dead." + (hadDrops ? " I have what they dropped for you." : ""));
                }
                go(sp, near.blockPosition(), 0);
                return TaskState.RUNNING;
            }
            default -> {
                return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
            }
        }
    }

    /** They died since it first saw them: dead now, or a new body under their name. */
    private boolean died(ServerPlayer now) {
        if (body == null) return false;
        if (!body.isAlive()) {
            if (diedAt == null) diedAt = body.blockPosition();
            return true;
        }
        if (now != null && now != body) {
            if (diedAt == null) diedAt = body.blockPosition();
            return true;
        }
        return false;
    }

    private TaskState dropsNext(ServerPlayer sp) {
        if (!com.mineagent.engine.settings.Switches.huntDrops() || diedAt == null) return end(true, record.name + " is dead.");
        phase = Phase.DROPS;
        ticks = 0;
        hadDrops = !sp.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(diedAt).inflate(6, 3, 6), ItemEntity::isAlive).isEmpty();
        return TaskState.RUNNING;
    }

    private boolean hadDrops;

    private void go(ServerPlayer sp, BlockPos p, int radius) {
        if (goingTo == null || goingTo.distSqr(p) > 4 || ++repath >= 40 && !nav.isNavigating()) {
            goingTo = p.immutable();
            repath = 0;
            if (radius <= 0) nav.navigateTo(p.getX(), p.getY(), p.getZ());
            else nav.navigateNear(p.getX(), p.getY(), p.getZ(), radius);
        }
        nav.tick();
    }

    private TaskState end(boolean ok, String line) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't finish the hunt: " + line + ".");
        phase = ok ? Phase.DONE : Phase.FAILED;
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
        goingTo = null;
        TaskContext.inputDriver(player).clear();
    }
}
