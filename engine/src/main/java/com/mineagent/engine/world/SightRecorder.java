package com.mineagent.engine.world;

import com.mineagent.engine.memory.Sightings;
import com.mineagent.engine.memory.Sightings.Kind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * What the companion sees, into its memory of things seen (SMH plan 70, MA5.7), as a player would see it: animals,
 * mobs and villagers it has a line of sight to, and ore and notable blocks with a face open to the air that its eyes
 * can see. Nothing through rock: the plan's "ore sight" is only ores it has seen.
 */
public final class SightRecorder {
    /** How often it looks at what moves, and at blocks, in ticks. */
    static final int ENTITIES_EVERY = 40;
    static final int BLOCKS_EVERY = 100;
    /** While it is on the move, a look at blocks every 8 blocks it goes, no closer than this many ticks apart. */
    static final int BLOCKS_MOVING = 20;
    /** How far it looks for what moves, and for blocks. */
    static final double ENTITY_RANGE = 24.0;
    static final int BLOCK_RANGE = 10;

    /** Blocks worth remembering besides ore. */
    static final Set<String> NOTABLE = Set.of("spawner", "trial_spawner", "vault", "bell", "enchanting_table",
            "brewing_stand", "anvil", "chipped_anvil", "damaged_anvil", "nether_portal", "end_portal_frame", "chest",
            "barrel", "beehive", "bee_nest", "lodestone", "respawn_anchor", "beacon", "conduit", "suspicious_sand",
            "suspicious_gravel", "budding_amethyst");

    private SightRecorder() {}

    /** What it sees this tick: what moves every two seconds; blocks when {@code lookAtBlocks} (its look schedule). */
    public static void tick(ServerPlayer sp, Sightings sightings, long gameTick, boolean lookAtBlocks,
                            java.util.function.Consumer<Sightings.Sighting> rare) {
        if (sp == null || sightings == null || !sp.isAlive() || sp.isSpectator()) return;
        try {
            if (gameTick % ENTITIES_EVERY == 0) entities(sp, sightings, gameTick);
            if (lookAtBlocks) blocks(sp, sightings, gameTick, rare);
        } catch (RuntimeException error) {
            System.err.println("[MineAgent] Sight recording failed: " + error.getMessage());
        }
    }

    private static void entities(ServerPlayer sp, Sightings sightings, long tick) {
        ServerLevel level = sp.serverLevel();
        String dim = level.dimension().location().toString();
        Map<String, int[]> seen = new HashMap<>();
        for (Entity e : level.getEntities(sp, sp.getBoundingBox().inflate(ENTITY_RANGE),
                e -> e.isAlive() && (e instanceof Animal || e instanceof Enemy || e instanceof AbstractVillager))) {
            if (!sp.hasLineOfSight(e)) continue;
            Kind kind = e instanceof AbstractVillager ? Kind.VILLAGER : e instanceof Enemy ? Kind.MOB : Kind.ANIMAL;
            String what = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            BlockPos p = e.blockPosition();
            String bucket = kind + ":" + what + "@" + (p.getX() >> 3) + "," + (p.getZ() >> 3);
            int[] agg = seen.computeIfAbsent(bucket, k -> new int[] {kind.ordinal(), p.getX(), p.getY(), p.getZ(), 0});
            agg[4]++;
        }
        for (var e : seen.entrySet()) {
            int[] a = e.getValue();
            String what = e.getKey().substring(e.getKey().indexOf(':') + 1, e.getKey().indexOf('@'));
            sightings.see(Kind.values()[a[0]], what, dim, a[1], a[2], a[3], a[4], tick);
        }
    }

    /** A first sighting this rare goes in the journal too. */
    static final double RARE = 0.9;

    private static void seeAndTell(Sightings sightings, Kind kind, String what, String dim, int x, int y, int z, int count,
                                   long tick, java.util.function.Consumer<Sightings.Sighting> rare) {
        boolean fresh = sightings.see(kind, what, dim, x, y, z, count, tick);
        String w = Sightings.base(what);
        if (fresh && rare != null && Sightings.importance(kind, w) >= RARE) {
            rare.accept(new Sightings.Sighting(kind, w, dim, x, y, z, count, tick, tick, 0));
        }
    }

    private static void blocks(ServerPlayer sp, Sightings sightings, long tick,
                               java.util.function.Consumer<Sightings.Sighting> rare) {
        ServerLevel level = sp.serverLevel();
        String dim = level.dimension().location().toString();
        Vec3 eye = sp.getEyePosition();
        BlockPos centre = BlockPos.containing(eye);
        Map<String, int[]> ore = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int lavaSeen = 0;
        for (int dx = -BLOCK_RANGE; dx <= BLOCK_RANGE; dx++) {
            for (int dy = -BLOCK_RANGE; dy <= BLOCK_RANGE; dy++) {
                for (int dz = -BLOCK_RANGE; dz <= BLOCK_RANGE; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    boolean isOre = id.endsWith("_ore") || id.equals("ancient_debris");
                    boolean notable = !isOre && NOTABLE.contains(id);
                    boolean lava = !isOre && !notable && lavaSeen < 4 && state.getFluidState().is(FluidTags.LAVA)
                            && state.getFluidState().isSource();
                    if (!isOre && !notable && !lava) continue;
                    BlockPos at = cursor.immutable();
                    if (!lava && !exposed(level, at)) continue;
                    if (!inSight(sp, level, eye, at)) continue;
                    if (isOre) {
                        String what = Sightings.base(id);
                        String bucket = what + "@" + (at.getX() >> 2) + "," + (at.getY() >> 2) + "," + (at.getZ() >> 2);
                        int[] agg = ore.computeIfAbsent(bucket, k -> new int[] {at.getX(), at.getY(), at.getZ(), 0});
                        agg[3]++;
                    } else if (notable) {
                        seeAndTell(sightings, Kind.BLOCK, id, dim, at.getX(), at.getY(), at.getZ(), 1, tick, rare);
                    } else {
                        lavaSeen++;
                        sightings.see(Kind.DANGER, "lava", dim, at.getX(), at.getY(), at.getZ(), 1, tick);
                    }
                }
            }
        }
        for (var e : ore.entrySet()) {
            int[] a = e.getValue();
            seeAndTell(sightings, Kind.ORE, e.getKey().substring(0, e.getKey().indexOf('@')), dim, a[0], a[1], a[2], a[3], tick, rare);
        }
    }

    /** A face open to air or water, so it can be seen at all. */
    static boolean exposed(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.isAir() || !n.getFluidState().isEmpty() || !n.canOcclude()) return true;
        }
        return false;
    }

    /** Its eyes reach the block with nothing solid between. */
    static boolean inSight(ServerPlayer sp, ServerLevel level, Vec3 eye, BlockPos pos) {
        BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, sp));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    /** A block was broken: if it was ore or a notable block it remembered, it is struck off. */
    public static void broken(Sightings sightings, String dim, BlockPos pos, BlockState old) {
        if (sightings == null || old == null) return;
        String id = BuiltInRegistries.BLOCK.getKey(old.getBlock()).getPath();
        if (id.endsWith("_ore") || id.equals("ancient_debris")) {
            sightings.gone(Kind.ORE, id, dim, pos.getX(), pos.getY(), pos.getZ());
        } else if (NOTABLE.contains(id)) {
            sightings.gone(Kind.BLOCK, id, dim, pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
