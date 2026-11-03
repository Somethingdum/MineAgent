package com.mineagent.engine.combat;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.engine.MineAgentEngine;
import com.mineagent.engine.loop.AgentLoop;
import com.mineagent.engine.task.TaskContext;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Whom the attack tools may hit (SMH plan 69, MA3.8): a player only on the owner's or a trusted player's word
 * ({@code /mineagent target <player>}; the owner's "stop" clears the list), never the owner, never a companion of
 * the same owner, never the owner's tamed animals. Mobs as before. Checked in code; what the model asks for is not
 * enough.
 */
public final class TargetPolicy {
    private TargetPolicy() {}

    /** Null when the companion may attack {@code target}; else the reason, for the tool's answer. */
    public static String refusal(AgentPlayer companion, Entity target) {
        if (companion == null || target == null) return "no target";
        UUID owner = companion.ownerUuid();
        if (target instanceof Player p) {
            if (p.getUUID().equals(owner)) return "never attacks its owner";
            if (owner != null && MineAgentEngine.isCompanionOwnedBy(p.getUUID(), owner)) return "never attacks a companion of the same owner";
            AgentLoop loop = TaskContext.agentLoop(companion);
            if (loop == null || !loop.getAgentSettings().isAllowedTarget(p.getUUID())) {
                return "not allowed to attack " + p.getName().getString() + " (only on the owner's word: /mineagent target "
                        + p.getName().getString() + ")";
            }
            return null;
        }
        if (target instanceof OwnableEntity pet && owner != null && owner.equals(pet.getOwnerUUID())) {
            return "never attacks its owner's " + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath();
        }
        return null;
    }
}
