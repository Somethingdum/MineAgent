// SMH plan 70, one agent: a companion's work done by its one agent (Agents: SecondBrainEngine's AltoClef on Automatone's
// Baritone, for the companion's body), as one of the companion's own tasks: an item it is to get (AltoClef's catalogue
// task for it), or its life on its own (Mindcraft's NPC routine over its goals). While it has the body, the agent's own
// chains keep it alive (mob defence, food, falls, lava and fire, unstuck) and the companion's stand down. Written for
// this mod.
package com.mineagent.engine.agent;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.agent.npc.NpcData;
import com.mineagent.engine.agent.npc.NpcRoutineTask;
import com.mineagent.engine.task.TaskContext;
import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.tasksystem.Task;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

public class AgentTask extends CompanionTask<AgentTask.AgentRecord> {

    /** What the agent is to do: {@code item} x {@code count} (an item AltoClef's catalogue knows), or its life (null item). */
    public static final class AgentRecord extends TaskRecord {
        public final String item;
        public final int count;
        /** For its life: its goals and routine (null for an item). */
        public final transient NpcData life;

        private AgentRecord(String toolCallId, String item, int count, NpcData life) {
            super(toolCallId);
            this.item = item;
            this.count = count;
            this.life = life;
        }

        public static AgentRecord item(String toolCallId, String item, int count) {
            return new AgentRecord(toolCallId, item, count, null);
        }

        public static AgentRecord life(String toolCallId, NpcData goals) {
            return new AgentRecord(toolCallId, null, 0, goals);
        }
    }

    private Task root;
    private Item item;
    private int target = -1;
    private String outcome = "";

    public AgentTask(AgentPlayer player, AgentRecord record) {
        super(player, record);
    }

    private ServerPlayer sp() {
        return TaskContext.serverPlayer(player);
    }

    @Override
    protected void onStart() {
        ServerPlayer sp = sp();
        AltoClefController agent = Agents.of(player);
        if (root == null) {
            if (record.item != null) {
                String name = record.item.contains(":") ? record.item.substring(record.item.indexOf(':') + 1) : record.item;
                item = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(name));
                target = count(sp) + record.count;
                root = TaskCatalogue.taskExists(name) ? TaskCatalogue.getItemTask(name, target) : null;
                if (root == null) outcome = "I don't know how to get " + name.replace('_', ' ') + ".";
                record.extendDeadlineTo(Math.max(record.deadline(), sp.level().getGameTime() + 24_000L));
            } else {
                root = new NpcRoutineTask(record.life != null ? record.life : new NpcData(), null);
                record.extendDeadlineTo(Long.MAX_VALUE / 4);
            }
        }
        if (root != null) agent.runUserTask(root);
    }

    private int count(ServerPlayer sp) {
        if (item == null) return 0;
        var inv = sp.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    @Override
    protected TaskState onTick() {
        if (root == null) return TaskState.FAILED;
        ServerPlayer sp = sp();
        if (!sp.isAlive()) return TaskState.RUNNING;
        Agents.tick(player);
        if (record.item != null) {
            int have = count(sp);
            if (have >= target) {
                int got = record.count;
                outcome = "Done: " + got + " " + record.item.replace('_', ' ') + (got == 1 ? "" : "s") + ".";
                return TaskState.SUCCESS;
            }
            if (sp.level().getGameTime() > record.deadline()) {
                outcome = "I couldn't get " + record.item.replace('_', ' ') + " in time.";
                return TaskState.FAILED;
            }
        }
        return TaskState.RUNNING;
    }

    @Override
    protected void onInterrupt() {
        AltoClefController agent = Agents.existing(player.companionId());
        if (agent != null) agent.cancelUserTask();
    }

    /** The agent's root task (its routine for a life), or null. */
    public Task root() {
        return root;
    }

    @Override
    protected String successMessage() {
        return outcome.isEmpty() ? "Done." : outcome;
    }

    @Override
    protected String failureMessage() {
        return outcome.isEmpty() ? "I couldn't do it." : outcome;
    }

    @Override
    protected String timeoutMessage() {
        return failureMessage();
    }
}
