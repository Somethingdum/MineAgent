// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/api/java/baritone/api/Settings.java
// at commit 11ea66c (branch 1.21.1). SMH: only the settings AltoClef's tasks read, with Baritone's own defaults and
// comments; no file I/O, no reflection over the fields. The companion's walks read them through its pathing.
package com.mineagent.engine.alto.baritone.api;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Baritone's settings, as AltoClef's tasks use them.
 */
public final class Settings {

    /** Allow Baritone to break blocks. */
    public final Setting<Boolean> allowBreak = new Setting<>(true);

    /** Allow Baritone to place blocks. */
    public final Setting<Boolean> allowPlace = new Setting<>(true);

    /** Allow Baritone to sprint. */
    public final Setting<Boolean> allowSprint = new Setting<>(true);

    /** Allow Baritone to move items in your inventory to your hotbar. */
    public final Setting<Boolean> allowInventory = new Setting<>(false);

    /** This should be monetary value of a block's worth of placing, in ticks. */
    public final Setting<Double> blockPlacementPenalty = new Setting<>(20D);

    /** This is just a tiebreaker to make it less likely to break blocks if it can avoid it. */
    public final Setting<Double> blockBreakAdditionalPenalty = new Setting<>(2D);

    /** You know what it is. */
    public final Setting<Boolean> allowParkour = new Setting<>(false);

    /** Allow parkour that places a block to jump from. */
    public final Setting<Boolean> allowParkourPlace = new Setting<>(false);

    /** Only actually takes effect if allowParkour is also true. */
    public final Setting<Boolean> allowParkourAscend = new Setting<>(true);

    /** Allow descending diagonally. Safer than allowParkour yet still slightly unsafe, can make contact with unchecked adjacent blocks. */
    public final Setting<Boolean> allowDiagonalDescend = new Setting<>(false);

    /** Allow diagonal ascending. Actually pretty safe, much safer than diagonal descend tbh. */
    public final Setting<Boolean> allowDiagonalAscend = new Setting<>(false);

    /** Allow a diagonal descend that overshoots its block. */
    public final Setting<Boolean> allowOvershootDiagonalDescend = new Setting<>(true);

    /** If we overshoot a traverse and end up one block beyond the destination, mark it as successful. */
    public final Setting<Boolean> overshootTraverse = new Setting<>(true);

    /** Blocks that Baritone is allowed to place (as throwaway, for sneak bridging, pillaring, etc.) */
    public final Setting<List<Item>> acceptableThrowawayItems = new Setting<>(new ArrayList<>(Arrays.asList(
            Blocks.DIRT.asItem(),
            Blocks.COBBLESTONE.asItem(),
            Blocks.NETHERRACK.asItem(),
            Blocks.STONE.asItem()
    )));

    /** Blocks that Baritone will attempt to avoid (Used in avoidance). */
    public final Setting<List<Block>> blocksToAvoid = new Setting<>(new ArrayList<>(List.of(
            Blocks.TRIPWIRE
    )));

    /** Blocks that Baritone is not allowed to break. */
    public final Setting<List<Block>> blocksToDisallowBreaking = new Setting<>(new ArrayList<>());

    /** Blocks that baritone shouldn't break, but can if it needs to. */
    public final Setting<List<Block>> blocksToAvoidBreaking = new Setting<>(new ArrayList<>(Arrays.asList(
            Blocks.CRAFTING_TABLE,
            Blocks.FURNACE,
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST
    )));

    /** If this setting is true, Baritone will never break a block that is adjacent to an unsupported falling block. */
    public final Setting<Boolean> avoidUpdatingFallingBlocks = new Setting<>(true);

    /** How far are you allowed to fall onto solid ground (without a water bucket)? 3 won't deal any damage. */
    public final Setting<Integer> maxFallHeightNoWater = new Setting<>(3);

    /** If a movement takes this many ticks more than its initial cost estimate, cancel it. */
    public final Setting<Integer> movementTimeoutTicks = new Setting<>(100);

    /** Pathing can never take longer than this, even if that means failing to find any path at all. */
    public final Setting<Long> failureTimeoutMS = new Setting<>(2000L);

    /** Planning ahead while executing a segment can never take longer than this. */
    public final Setting<Long> planAheadFailureTimeoutMS = new Setting<>(5000L);

    /** While mining, should it also consider dropped items of the correct type as a pathing destination? */
    public final Setting<Boolean> mineScanDroppedItems = new Setting<>(true);

    /** While mining, wait this number of milliseconds after mining an ore to see if it will drop an item. */
    public final Setting<Long> mineDropLoiterDurationMSThanksLouca = new Setting<>(250L);

    /** Move without having to force the client-sided rotations. */
    public final Setting<Boolean> freeLook = new Setting<>(true);

    /** How many degrees to randomize the pitch and yaw every tick. Set to 0 to disable. */
    public final Setting<Double> randomLooking = new Setting<>(0.01d);

    /** Start fading out the path at 20 movements ahead, and stop rendering it entirely 30 movements ahead. */
    public final Setting<Boolean> fadePath = new Setting<>(false);

    /** The A* heuristic's multiplier: an underestimate keeps the best path found; higher is faster and worse. */
    public final Setting<Double> costHeuristic = new Setting<>(3.563);

    /** The "axis" command (aka GoalAxis) will go to a axis, or diagonal axis, at this Y level. */
    public final Setting<Integer> axisHeight = new Setting<>(120);

    /** Don't stop walking forward when you need to break blocks in your way. */
    public final Setting<Boolean> walkWhileBreaking = new Setting<>(true);

    /** When true, the player will remain with its existing look direction as often as possible. */
    public final Setting<Boolean> remainWithExistingLookDirection = new Setting<>(true);

    /** Allow Baritone to fall arbitrary distances and place a water bucket beneath it. */
    public final Setting<Boolean> allowWaterBucketFall = new Setting<>(true);

    /** Allow mining the block directly beneath its feet. */
    public final Setting<Boolean> allowDownward = new Setting<>(true);

    /** Disallow MineBehavior from using X-Ray to see where the ores are. Turn this option on to force it to mine "legit". */
    public final Setting<Boolean> legitMine = new Setting<>(false);

    /** Treat every liquid next to a block as one that would flow in when it is broken. Enable for mods' own fluid physics. */
    public final Setting<Boolean> strictLiquidCheck = new Setting<>(false);

    /** A setting: its value, and the value it started with. */
    public static final class Setting<T> {
        public T value;
        public final T defaultValue;

        private Setting(T value) {
            if (value == null) {
                throw new IllegalArgumentException("Cannot determine value type class from null");
            }
            this.value = value;
            this.defaultValue = value;
        }

        @Deprecated
        public T get() {
            return value;
        }

        /** Reset this setting to its default value. */
        public void reset() {
            value = defaultValue;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }
}
