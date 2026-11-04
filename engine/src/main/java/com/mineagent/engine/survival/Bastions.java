package com.mineagent.engine.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.AABB;

/**
 * Bastions (SMH plan 70, MA11.1): breaking a block or opening a chest inside one with piglins near turns every piglin
 * that sees it on the player, gold armour or not. So it does neither there while piglins are within 16 blocks.
 */
public final class Bastions {
    private Bastions() {}

    /** Whether breaking or opening at {@code pos} would anger the piglins: in a bastion, with one within 16 blocks. */
    public static boolean angers(ServerLevel level, BlockPos pos, ServerPlayer self) {
        if (level == null || pos == null || level.dimension() != net.minecraft.world.level.Level.NETHER) return false;
        try {
            var structure = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .get(BuiltinStructures.BASTION_REMNANT);
            if (structure == null || !level.structureManager().getStructureWithPieceAt(pos, structure).isValid()) return false;
            return !level.getEntitiesOfClass(AbstractPiglin.class, new AABB(pos).inflate(16.0), AbstractPiglin::isAlive).isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
