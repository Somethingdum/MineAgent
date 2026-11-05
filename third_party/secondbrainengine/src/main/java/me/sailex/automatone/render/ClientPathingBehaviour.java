/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.sailex.automatone.render;

import me.sailex.automatone.api.pathing.calc.IPathFinder;
import me.sailex.automatone.api.pathing.goals.Goal;
import net.minecraft.world.entity.Entity;
import java.util.Optional;

public class ClientPathingBehaviour {
    public final Entity entity;
    private RenderedPath current;
    private RenderedPath next;

    public ClientPathingBehaviour(Entity entity) {
        this.entity = entity;
    }

    public Goal getGoal() {
        // Reaching across sides is fun
//        return Optional.ofNullable(MinecraftClient.getInstance().getServer())
//                .map(s -> s.getWorld(this.entity.getWorld().getRegistryKey()))
//                .map(w -> w.getEntity(this.entity.getUuid()))
//                .map(IBaritone.KEY::getNullable)
//                .map(IBaritone::getPathingBehavior)
//                .map(IPathingBehavior::getGoal)
//                .orElse(null);
        return null;
    }

    public Optional<? extends IPathFinder> getInProgress() {
        // Reaching across sides is fun
//        return Optional.ofNullable(MinecraftClient.getInstance().getServer())
//                .map(s -> s.getWorld(this.entity.getWorld().getRegistryKey()))
//                .map(w -> w.getEntity(this.entity.getUuid()))
//                .map(IBaritone.KEY::getNullable)
//                .map(IBaritone::getPathingBehavior)
//                .flatMap(IPathingBehavior::getInProgress);
        return Optional.empty();
    }

    public RenderedPath getCurrent() {
        return this.current;
    }

    public RenderedPath getNext() {
        return this.next;
    }
}
