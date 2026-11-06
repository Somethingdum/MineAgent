// SMH plan 70: the companion's own rules for what Baritone (and AltoClef's tasks on it) may break and place, kept as
// the companion's own pathfinder keeps them (pathing/moves/MovementHelper.costOfBreaking): what a person placed is never
// broken (MA5.2), nothing is broken or placed at a farm whose player is AFK or in an area its owner protected (MA5.4,
// MA10.2), and a block that is built, grows food back or hides a silverfish is never broken just to get somewhere
// (MA5.1). Written for this mod; no other project's code.
package com.mineagent.engine.rules;

import com.mineagent.engine.pathing.util.BlockHelper;
import com.mineagent.engine.pathing.util.BreakCost;
import com.mineagent.engine.settings.Switches;
import com.mineagent.engine.world.AfkFarms;
import com.mineagent.engine.world.PlacedBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class SmhRules {

    /** Each level's record of what people placed, taken on the server thread (Baritone's searches run on its own). */
    private static final Map<ServerLevel, PlacedBlocks> PLACED = Collections.synchronizedMap(new WeakHashMap<>());

    private SmhRules() {}

    /** On the server thread, before the companion's tick: its level's record ready for the searches. */
    public static void prime(ServerLevel level) {
        if (level != null && !PLACED.containsKey(level)) PLACED.put(level, PlacedBlocks.of(level));
    }

    /** Whether a real player placed the block at {@code pos} (never broken, with "never break built blocks" on). */
    public static boolean placedByAPerson(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !Switches.neverBreakBuilt()) return false;
        PlacedBlocks placed = PLACED.get(level);
        return placed != null && placed.isPlaced(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Whether {@code pos} is at a farm whose player is AFK, or in an area its owner protected. */
    public static boolean afkFarm(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        return AfkFarms.protects(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Whether a block of this kind is never broken to get somewhere: one someone built (unless "never break built
     * blocks" is off), food that grows back, an infested block. A block it was sent to mine is not asked this.
     */
    public static boolean neverBreakToPass(BlockState state) {
        BreakCost.Kind kind = BlockHelper.kindOf(state);
        if (kind == BreakCost.Kind.BUILT && !Switches.neverBreakBuilt()) return false;
        return kind != BreakCost.Kind.NATURAL;
    }
}
