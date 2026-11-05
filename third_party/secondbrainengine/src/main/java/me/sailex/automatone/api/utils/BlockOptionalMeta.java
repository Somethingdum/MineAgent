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

package me.sailex.automatone.api.utils;

import me.sailex.automatone.api.utils.accessor.IItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public final class BlockOptionalMeta {

    private final Block block;
    private final Set<BlockState> blockstates;
    private final IntSet stateHashes;
    private final IntSet stackHashes;
    private static final Pattern pattern = Pattern.compile("^(.+?)(?::(\\d+))?$");
    private static final Map<Block, List<Item>> drops = new HashMap<>();

    public BlockOptionalMeta(ServerLevel world, @Nonnull Block block) {
        this.block = block;
        this.blockstates = getStates(block);
        this.stateHashes = getStateHashes(blockstates);
        this.stackHashes = getStackHashes(world, blockstates);
    }

    public BlockOptionalMeta(ServerLevel world, @Nonnull String selector) {
        Matcher matcher = pattern.matcher(selector);

        if (!matcher.find()) {
            throw new IllegalArgumentException("invalid block selector");
        }

        MatchResult matchResult = matcher.toMatchResult();

        block = BlockUtils.stringToBlockRequired(matchResult.group(1));
        blockstates = getStates(block);
        stateHashes = getStateHashes(blockstates);
        stackHashes = getStackHashes(world, blockstates);
    }

    private static Set<BlockState> getStates(@Nonnull Block block) {
        return new HashSet<>(block.getStateDefinition().getPossibleStates());
    }

    private static IntSet getStateHashes(Set<BlockState> blockstates) {
        return blockstates.stream()
                        .map(BlockState::hashCode)
                        .collect(Collectors.toCollection(IntOpenHashSet::new));
    }

    private static IntSet getStackHashes(ServerLevel world, Set<BlockState> blockstates) {
        //noinspection ConstantConditions
        return blockstates.stream()
                        .flatMap(state -> drops(world, state.getBlock())
                                .stream()
                                .map(item -> new ItemStack(item, 1))
                        )
                        .map(stack -> ((IItemStack) (Object) stack).getBaritoneHash())
                        .collect(Collectors.toCollection(IntOpenHashSet::new));
    }

    public Block getBlock() {
        return block;
    }

    public boolean matches(@Nonnull Block block) {
        return block == this.block;
    }

    public boolean matches(@Nonnull BlockState blockstate) {
        return blockstate.getBlock() == this.block && stateHashes.contains(blockstate.hashCode());
    }

    public boolean matches(ItemStack stack) {
        //noinspection ConstantConditions
        int hash = ((IItemStack) (Object) stack).getBaritoneHash();

        hash -= stack.getDamageValue();

        return stackHashes.contains(hash);
    }

    @Override
    public String toString() {
        return String.format("BlockOptionalMeta{block=%s}", block);
    }

    public BlockState getAnyBlockState() {
        if (!blockstates.isEmpty()) {
            return blockstates.iterator().next();
        }

        return null;
    }

    // TODO check if erasing the metadata of both the block and the drops is a good idea
    private static synchronized List<Item> drops(ServerLevel world, Block b) {
        return drops.computeIfAbsent(b, block -> {
            
            Optional<ResourceKey<LootTable>> lootTableLocation = Optional.of(block.getLootTable());
            
            if ( lootTableLocation.isEmpty()) {
                return Collections.emptyList();
            } else {
                List<Item> items = new ArrayList<>();
                
                LootTable table = world.getServer().reloadableRegistries().getLootTable(lootTableLocation.get());
                
                table.getRandomItems(
                    new LootContext.Builder(new LootParams.Builder(world)
                            .withParameter(LootContextParams.ORIGIN, Vec3.atLowerCornerOf(BlockPos.ZERO))
                            .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, null)
                            .withParameter(LootContextParams.BLOCK_STATE, block.defaultBlockState())
                            .create(LootContextParamSets.BLOCK))
                            .withOptionalRandomSeed(world.getSeed())
                            .create(null),
                    stack -> items.add(stack.getItem())
                );
                return items;
            }
        });
    }
}
