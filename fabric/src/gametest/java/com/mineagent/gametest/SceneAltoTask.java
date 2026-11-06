package com.mineagent.gametest;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.CompanionTaskFactory;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasksystem.Task;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * SMH plan 71: an AltoClef task a scene gives its companion, run where the companion's own AltoClef tasks run
 * (AltoTreeTask): inside its task auction, in the engine's tick, just before the body's physics step. Ticked from
 * anywhere else (the game-test ticker, the tick's end), its keys come after that step and the idle planner's, so it
 * cannot walk the body. Game tests only.
 */
public final class SceneAltoTask extends CompanionTask<SceneAltoTask.Record> {
    /** The task, made inside the companion's AltoClef scope when it starts. */
    public static final class Record extends TaskRecord {
        final Supplier<Task> root;

        public Record(String id, Supplier<Task> root) {
            super(id);
            this.root = root;
        }
    }

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    /** Lets the engine make this task from its record (once per run). */
    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) CompanionTaskFactory.register(Record.class, SceneAltoTask::new);
    }

    private Task root;
    private volatile boolean done;

    private SceneAltoTask(AgentPlayer player, Record record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (root == null) root = AltoClef.of(player()).within(record().root::get);
    }

    @Override
    protected TaskState onTick() {
        AltoClef host = AltoClef.of(player());
        host.tick(root);
        final Task tree = root;
        if (host.within(tree::isFinished)) {
            done = true;
            return TaskState.SUCCESS;
        }
        return TaskState.RUNNING;
    }

    @Override
    protected void onInterrupt() {
    }
}
