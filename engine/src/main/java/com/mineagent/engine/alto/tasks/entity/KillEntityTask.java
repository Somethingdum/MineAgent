// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/entity/KillEntityTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.entity;

import com.mineagent.engine.alto.AltoClef;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.entity.Entity;

/**
 * Kill a specific entity
 */
public class KillEntityTask extends AbstractKillEntityTask {

    private final Entity target;

    public KillEntityTask(Entity entity) {
        target = entity;
    }

    public KillEntityTask(Entity entity, double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
        super(maintainDistance, combatGuardLowerRange, combatGuardLowerFieldRadius);
        target = entity;
    }

    @Override
    protected Optional<Entity> getEntityTarget(AltoClef mod) {
        return Optional.of(target);
    }

    @Override
    protected boolean isSubEqual(AbstractDoToEntityTask other) {
        if (other instanceof KillEntityTask task) {
            return Objects.equals(task.target, target);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Killing " + target.getType().getDescriptionId();
    }
}
