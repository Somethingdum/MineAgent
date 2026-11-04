package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.planning.end.EyeTriangulation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Finding the stronghold (SMH plan 70, MA11.5), as a player does it: an eye of ender thrown and watched (it flies a
 * few blocks toward the stronghold, then drops or breaks), a walk to the side, a second eye, and where their lines
 * cross is where to dig. The eyes that drop are picked up. The place is remembered as "stronghold".
 */
public class EyeTask extends CompanionTask<EyeTask.EyeRecord> {
    public static final class EyeRecord extends TaskRecord {
        public EyeRecord(String toolCallId) {
            super(toolCallId);
        }
    }

    private enum Phase { THROW, WATCH, PICK, MOVE, DONE, FAILED }

    /** How far apart the eyes are: the lines cross at a good angle for a stronghold some hundreds of blocks off. */
    static final double APART = 64;

    private Phase phase = Phase.THROW;
    private PlayerNav nav;
    private final List<EyeTriangulation.Throw> eyes = new ArrayList<>();
    private Vec3 from;
    private EyeOfEnder eye;
    private BlockPos second;
    private int ticks, tries;
    private String outcome = "";

    public EyeTask(AgentPlayer player, EyeRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        record.extendDeadlineTo(Math.max(record.deadline(), TaskContext.serverPlayer(player).level().getGameTime() + 12_000L));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ticks++;
        try {
            switch (phase) {
                case THROW -> {
                    if (sp.serverLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD) return end(false, "eyes only lead to the stronghold in the Overworld");
                    if (ProductionTask.count(sp, Items.ENDER_EYE) == 0) return end(false, "I have no eye of ender");
                    if (!TaskContext.selectInventoryItemForHand(player, "minecraft:ender_eye", InteractionHand.MAIN_HAND)) return TaskState.RUNNING;
                    from = sp.position();
                    sp.setXRot(-10.0f);
                    int before = ProductionTask.count(sp, Items.ENDER_EYE);
                    Interaction.useItem(sp, InteractionHand.MAIN_HAND);
                    TaskContext.syncInventory(sp);
                    eye = null;
                    for (EyeOfEnder e : sp.serverLevel().getEntitiesOfClass(EyeOfEnder.class, sp.getBoundingBox().inflate(4))) eye = e;
                    if (eye == null && ProductionTask.count(sp, Items.ENDER_EYE) >= before) {
                        return end(false, "the eye won't fly: there is no stronghold in this world for it to find");
                    }
                    phase = Phase.WATCH;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case WATCH -> {
                    // Its flight on the ground: where it is now, from where it was thrown.
                    if (eye == null) {
                        for (EyeOfEnder e : sp.serverLevel().getEntitiesOfClass(EyeOfEnder.class, new AABB(BlockPos.containing(from)).inflate(16))) eye = e;
                    }
                    if (eye != null && eye.isAlive()) {
                        Vec3 d = eye.position().subtract(from);
                        if (d.horizontalDistance() >= 6.0 || ticks > 60) {
                            eyes.add(new EyeTriangulation.Throw(from.x, from.z, d.x, d.z));
                            System.out.println("[MineAgent] eye flew " + String.format(java.util.Locale.ROOT, "%.1f, %.1f", d.x, d.z) + " from " + BlockPos.containing(from).toShortString());
                            phase = Phase.PICK;
                            ticks = 0;
                        }
                        return TaskState.RUNNING;
                    }
                    if (ticks > 80) return end(false, "I lost sight of the eye");
                    return TaskState.RUNNING;
                }
                case PICK -> {
                    // The eye drops after its flight (or breaks): the one that drops picked up.
                    var drops = sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new AABB(BlockPos.containing(from)).inflate(16, 8, 16), e -> e.isAlive() && e.getItem().is(Items.ENDER_EYE));
                    boolean flying = eye != null && eye.isAlive();
                    if (drops.isEmpty() && (!flying && ticks > 60 || ticks > 200)) {
                        nav.cancel();
                        return afterThrow(sp);
                    }
                    if (!drops.isEmpty()) {
                        var d = drops.get(0);
                        if (ticks % 10 == 1) nav.navigateTo(d.getBlockX(), d.getBlockY(), d.getBlockZ());
                        nav.tick();
                        if (ticks > 300) return afterThrow(sp);
                    }
                    return TaskState.RUNNING;
                }
                case MOVE -> {
                    if (sp.blockPosition().distSqr(second) <= 4 * 4 || ticks > 2_400) {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        phase = Phase.THROW;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (ticks % 40 == 1) nav.navigateNear(second.getX(), second.getY(), second.getZ(), 3);
                    nav.tick();
                    return TaskState.RUNNING;
                }
                default -> {
                    return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /** One throw done: the first sends it to the side for the second; two (or more) give the place. */
    private TaskState afterThrow(ServerPlayer sp) {
        if (eyes.size() >= 2) {
            var at = EyeTriangulation.locate(eyes);
            if (at.isEmpty()) {
                if (++tries > 2) return end(false, "the eyes flew too nearly the same way to tell where it is");
                return goToSide(sp, APART * 2);
            }
            int x = (int) Math.round(at.get()[0]), z = (int) Math.round(at.get()[1]);
            var loop = TaskContext.agentLoop(player);
            if (loop != null) {
                String dim = sp.serverLevel().dimension().location().toString();
                loop.placeKeeper().memory().found("stronghold", new com.mineagent.engine.world.RouteMemory.Point(dim, x, 30, z), sp.serverLevel().getGameTime());
                loop.journal().record("find", "the stronghold, by the eyes, about at " + x + ", " + z, sp.serverLevel().getGameTime(), dim, x, 30, z);
            }
            int far = (int) Math.round(Math.hypot(x - sp.getX(), z - sp.getZ()));
            return end(true, "The stronghold is about at " + x + ", " + z + ", " + far + " blocks from here; dig down there.");
        }
        return goToSide(sp, APART);
    }

    private TaskState goToSide(ServerPlayer sp, double apart) {
        var last = eyes.get(eyes.size() - 1);
        double[] p = EyeTriangulation.secondThrowFrom(last, apart);
        int x = (int) Math.round(p[0]), z = (int) Math.round(p[1]);
        int y = sp.serverLevel().isLoaded(new BlockPos(x, sp.getBlockY(), z))
                ? sp.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : sp.getBlockY();
        second = new BlockPos(x, y, z);
        phase = Phase.MOVE;
        ticks = 0;
        return TaskState.RUNNING;
    }

    private TaskState end(boolean ok, String line) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't find the stronghold: " + line + ".");
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
        TaskContext.inputDriver(player).clear();
    }
}
