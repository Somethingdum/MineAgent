package com.mineagent.engine.alto;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.alto.tasks.life.OwnLifeTask;
import com.mineagent.engine.task.TaskContext;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Its first days on its own done by AltoClef's priority tasks (SMH plan 70, behaviour.altoLife): OwnLifeTask on the
 * companion's body through its AltoClef host, as an order for an item is (AltoTreeTask). It ends once its owner's minimum
 * is there and the night's mobs have burned ("Done: ..."), or at its deadline; another task taking the body pauses it.
 */
public class AltoLifeTask extends CompanionTask<AltoLifeTask.LifeRecord> {

    /** Its life on its own; {@code hut}: where its hut is or goes (null: where it stands when it first builds). */
    public static final class LifeRecord extends TaskRecord {
        public final BlockPos hut;

        public LifeRecord(String toolCallId, BlockPos hut) {
            super(toolCallId);
            this.hut = hut == null ? null : hut.immutable();
        }
    }

    /** How long it may run before it is let go and chosen again (half a game day). */
    public static final long RUN_TICKS = 12_000L;

    private OwnLifeTask root;
    private boolean ended;
    private String outcome = "";
    private int ticks;

    public AltoLifeTask(AgentPlayer player, LifeRecord record) {
        super(player, record);
    }

    private ServerPlayer sp() {
        return TaskContext.serverPlayer(player);
    }

    @Override
    protected void onStart() {
        ServerPlayer sp = sp();
        AltoClef host = AltoClef.of(player);
        if (root == null) {
            root = host.within(() -> new OwnLifeTask(record.hut));
            record.extendDeadlineTo(Math.max(record.deadline(), sp.level().getGameTime() + RUN_TICKS));
            if (Boolean.getBoolean("mineagent.traceWork")) System.out.println("[MineAgent] alto: life (hut " + record.hut + ")");
        }
        ended = false;
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = sp();
        if (!sp.isAlive()) return TaskState.RUNNING;
        AltoClef host = AltoClef.of(player);
        host.tick(root);
        if (++ticks % 40 == 0) {
            AltoTreeTask.remember(player.companionId(), host, sp, ticks);
            recordHut(host, sp);
        }
        final OwnLifeTask tree = root;
        if (host.within(tree::isFinished)) {
            outcome = "Done: my tools, a hut with its crafting table and furnace, fuel and food" + (tree.hut() == null ? "." : ", at " + tree.hut().toShortString() + ".");
            ended = true;
            return TaskState.SUCCESS;
        }
        if (sp.level().getGameTime() > record.deadline()) {
            outcome = "Still on my first days: " + host.within(tree::toString) + ".";
            ended = true;
            return TaskState.FAILED;
        }
        return TaskState.RUNNING;
    }

    @Override
    protected void onInterrupt() {
        AltoClef host = AltoClef.of(player);
        if (ended) {
            host.stop(root, null);
            root = null;
        } else {
            host.stop(root, root);
        }
    }

    private boolean hutRecorded;

    /**
     * Its hut, once it stands, kept as a hut it built is (BlueprintTask): in its journal ("built the hut") and among its
     * places, so its nights, its base and its owner's questions find it.
     */
    private void recordHut(AltoClef host, ServerPlayer sp) {
        final OwnLifeTask tree = root;
        if (hutRecorded || tree == null || tree.hut() == null) return;
        BlockPos at = tree.hut();
        if (!host.within(() -> com.mineagent.engine.alto.tasks.construction.BuildHutTask.shellStands(AltoClef.getInstance(), at))) return;
        hutRecorded = true;
        var loop = TaskContext.agentLoop(player);
        if (loop == null) return;
        String dim = sp.level().dimension().location().toString();
        loop.journal().record("built", "built the hut (Mindcraft's dirt shelter) at " + at.toShortString(), sp.level().getGameTime());
        loop.placeKeeper().memory().name("build", "hut_dirt_shelter|dirt|oak_door",
                new com.mineagent.engine.world.RouteMemory.Point(dim, at.getX(), at.getY(), at.getZ()), sp.level().getGameTime());
    }

    /** Where its hut is, once chosen, or null. */
    public BlockPos hut() {
        final OwnLifeTask tree = root;
        return tree == null ? record.hut : tree.hut();
    }

    @Override
    protected String successMessage() {
        return outcome.isEmpty() ? "Done." : outcome;
    }

    @Override
    protected String failureMessage() {
        return outcome.isEmpty() ? "I couldn't get through my first days on my own." : outcome;
    }

    @Override
    protected String timeoutMessage() {
        return failureMessage();
    }
}
