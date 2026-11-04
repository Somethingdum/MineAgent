package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.AbstractDoToClosestObjectTask;
import adris.altoclef.tasksystem.Task;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Finds the closest entity and runs a task on that entity
 */
@SuppressWarnings("rawtypes")
public class DoToClosestEntityTask extends AbstractDoToClosestObjectTask<Entity> {

    private final Class[] targetEntities;

    private final Supplier<Vec3> getOriginPos;

    private final Function<Entity, Task> getTargetTask;

    private final Predicate<Entity> shouldInteractWith;

    public DoToClosestEntityTask(Supplier<Vec3> getOriginSupplier, Function<Entity, Task> getTargetTask, Predicate<Entity> shouldInteractWith, Class... entities) {
        getOriginPos = getOriginSupplier;
        this.getTargetTask = getTargetTask;
        this.shouldInteractWith = shouldInteractWith;
        targetEntities = entities;
    }

    public DoToClosestEntityTask(Supplier<Vec3> getOriginSupplier, Function<Entity, Task> getTargetTask, Class... entities) {
        this(getOriginSupplier, getTargetTask, entity -> true, entities);
    }

    public DoToClosestEntityTask(Function<Entity, Task> getTargetTask, Predicate<Entity> shouldInteractWith, Class... entities) {
        this(null, getTargetTask, shouldInteractWith, entities);
    }

    public DoToClosestEntityTask(Function<Entity, Task> getTargetTask, Class... entities) {
        this(null, getTargetTask, entity -> true, entities);
    }

    @Override
    protected Vec3 getPos(AltoClef mod, Entity obj) {
        return obj.position();
    }

    @Override
    protected Optional<Entity> getClosestTo(AltoClef mod, Vec3 pos) {
        if (!mod.getEntityTracker().entityFound(targetEntities)) return Optional.empty();
        return mod.getEntityTracker().getClosestEntity(pos, shouldInteractWith, targetEntities);
    }

    @Override
    protected Vec3 getOriginPos(AltoClef mod) {
        if (getOriginPos != null) {
            return getOriginPos.get();
        }
        return mod.getPlayer().position();
    }

    @Override
    protected Task getGoalTask(Entity obj) {
        return getTargetTask.apply(obj);
    }

    @Override
    protected boolean isValid(AltoClef mod, Entity obj) {
        return obj.isAlive() && mod.getEntityTracker().isEntityReachable(obj);
    }

    @Override
    protected void onStart() {
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof DoToClosestEntityTask task) {
            return Arrays.equals(task.targetEntities, targetEntities);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Doing something to closest entity...";
    }
}