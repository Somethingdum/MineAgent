package com.mineagent.engine.world;

import com.mineagent.engine.MineAgentEngine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * The blocks real players placed, per dimension, saved with the world itself (SMH plan 70, MA5.2): so a wall you built
 * of stone or dirt is never taken for a hillside. Nothing the companion places counts (its own bridges and pillars are
 * its to clear). A block someone breaks is struck off. The record holds positions only.
 *
 * <p>Fed by a mixin on the game's own block placing and breaking in each loader ({@code BlockItemMixin},
 * {@code ServerPlayerGameModeMixin}).
 */
public final class PlacedBlocks extends SavedData {
    static final String NAME = "mineagent_placed_blocks";

    private final PlacedBits bits = new PlacedBits();

    public PlacedBlocks() {}

    public static PlacedBlocks of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlacedBlocks::new, PlacedBlocks::load, null), NAME);
    }

    /** The record for this level, or null when the level isn't a server level. */
    public static PlacedBlocks of(Level level) {
        return level instanceof ServerLevel server ? of(server) : null;
    }

    /** A real player placed this block (both halves of a door or bed count). */
    public static void placedBy(Player player, Level level, BlockPos pos, BlockState state) {
        if (player == null || pos == null || MineAgentEngine.isCompanionPlayer(player.getUUID())) return;
        if (!com.mineagent.engine.settings.Switches.rememberPlaced()) return;
        PlacedBlocks record = of(level);
        if (record == null) return;
        record.mark(pos);
        if (state != null && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            record.mark(state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    ? pos.above() : pos.below());
        }
        if (state != null && state.hasProperty(BlockStateProperties.BED_PART)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING); // from the foot to the head
            record.mark(state.getValue(BlockStateProperties.BED_PART)
                    == net.minecraft.world.level.block.state.properties.BedPart.FOOT
                    ? pos.relative(facing) : pos.relative(facing.getOpposite()));
        }
    }

    /** Anyone broke this block: it is no longer one someone placed. */
    public static void broken(Level level, BlockPos pos) {
        PlacedBlocks record = of(level);
        if (record != null && pos != null) record.clear(pos);
    }

    public void mark(BlockPos pos) {
        bits.mark(pos.getX(), pos.getY(), pos.getZ());
        setDirty();
    }

    public void clear(BlockPos pos) {
        if (!bits.isMarked(pos.getX(), pos.getY(), pos.getZ())) return;
        bits.clear(pos.getX(), pos.getY(), pos.getZ());
        setDirty();
    }

    public boolean isPlaced(int x, int y, int z) {
        return bits.isMarked(x, y, z);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var e : bits.export().entrySet()) {
            CompoundTag section = new CompoundTag();
            section.putLong("k", e.getKey());
            section.put("b", new LongArrayTag(e.getValue()));
            list.add(section);
        }
        tag.put("sections", list);
        return tag;
    }

    static PlacedBlocks load(CompoundTag tag, HolderLookup.Provider registries) {
        PlacedBlocks record = new PlacedBlocks();
        Map<Long, long[]> saved = new HashMap<>();
        ListTag list = tag.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag section = list.getCompound(i);
            saved.put(section.getLong("k"), section.getLongArray("b"));
        }
        record.bits.load(saved);
        return record;
    }
}
