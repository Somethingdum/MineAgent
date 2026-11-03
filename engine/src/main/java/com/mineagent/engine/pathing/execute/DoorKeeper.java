package com.mineagent.engine.pathing.execute;

import com.mineagent.engine.act.Interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Closes what it opened (SMH plan 70, MA5.3): a door, trapdoor or gate the companion opened on its way is shut once it
 * has passed, and a lever it pulled for an iron door is pulled back. Not while anyone else stands in or near the
 * doorway (it never shuts a door on you), and not one someone else has since shut or broken. One keeper per companion,
 * ticked on the server thread.
 */
public final class DoorKeeper {
    /** It has passed once its feet are this far from the doorway's centre. */
    static final double PASSED = 1.6;
    /** Beyond this it has gone on without closing (out of reach); the door is let be. */
    static final double LEFT_BEHIND = 4.0;
    /** Anyone else this close to the doorway keeps it open. */
    static final double SOMEONE_NEAR = 3.0;
    /** A door still held open by someone after this long is let be. */
    static final long GIVE_UP_TICKS = 400;

    private record Opened(BlockPos door, BlockPos lever, long tick) {}

    private final List<Opened> opened = new ArrayList<>();

    /** It opened this door (either half) by hand. */
    public synchronized void opened(Level level, BlockPos door, long tick) {
        add(lowerHalf(level, door), null, tick);
    }

    /** It opened this iron door by pulling this lever (a button needs no undoing). */
    public synchronized void pulled(Level level, BlockPos door, BlockPos lever, long tick) {
        add(lowerHalf(level, door), lever, tick);
    }

    private void add(BlockPos door, BlockPos lever, long tick) {
        for (Opened o : opened) if (o.door().equals(door)) return;
        opened.add(new Opened(door.immutable(), lever == null ? null : lever.immutable(), tick));
    }

    public synchronized boolean isEmpty() {
        return opened.isEmpty();
    }

    /** Close what it has passed. */
    public synchronized void tick(ServerPlayer body, long tick) {
        if (opened.isEmpty() || body == null || !body.isAlive()) return;
        Level level = body.level();
        for (Opened o : List.copyOf(opened)) {
            if (!level.isLoaded(o.door())) {
                opened.remove(o);
                continue;
            }
            BlockState state = level.getBlockState(o.door());
            if (!isOpen(state) || tick - o.tick() > GIVE_UP_TICKS) {
                opened.remove(o); // shut or broken by someone, or held open too long
                continue;
            }
            double dx = body.getX() - (o.door().getX() + 0.5);
            double dy = body.getY() - o.door().getY();
            double dz = body.getZ() - (o.door().getZ() + 0.5);
            double away = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (away < PASSED || someoneElseNear(level, body, o.door())) continue;
            if (away > LEFT_BEHIND) {
                opened.remove(o);
                continue;
            }
            BlockPos target = o.lever() != null && level.getBlockState(o.lever()).getBlock() instanceof LeverBlock
                    ? o.lever() : o.door();
            body.setShiftKeyDown(false);
            Interaction.interactBlock(body, target, InteractionHand.MAIN_HAND);
            opened.remove(o);
        }
    }

    /** A door, trapdoor or gate standing open. */
    static boolean isOpen(BlockState state) {
        if (state == null || !state.hasProperty(BlockStateProperties.OPEN)) return false;
        boolean openable = state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock;
        return openable && state.getValue(BlockStateProperties.OPEN);
    }

    private static boolean someoneElseNear(Level level, ServerPlayer body, BlockPos door) {
        AABB doorway = new AABB(door).expandTowards(0, 1, 0);
        if (!level.getEntitiesOfClass(LivingEntity.class, doorway, e -> e != body && e.isAlive()).isEmpty()) return true;
        AABB near = doorway.inflate(SOMEONE_NEAR);
        return !level.getEntitiesOfClass(Player.class, near, p -> p != body && p.isAlive() && !p.isSpectator()).isEmpty();
    }

    /** A door's lower half, so both halves count as one door. */
    static BlockPos lowerHalf(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }
}
