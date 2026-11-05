// SMH plan 70: the companion's shabby hut, made the AltoClef way. The hut is Mindcraft's dirt_shelter blueprint
// (src/agent/npc/construction/dirt_shelter.json; Mindcraft, MIT, Copyright (c) 2024 Kolby Nottingham; see
// THIRD_PARTY_NOTICES.md), read as Mindcraft's BuildGoal reads one (blocks[y][z][x], its first layer `offset` below the
// hut's place, "" for any block), and built by Baritone's builder as AltoClef's PlaceBlockTask drives it (adris/altoclef/
// tasks/construction/PlaceBlockTask.java, MIT, Copyright (c) 2020 Adris Jautakas): its material task when it is short of
// blocks, its progress check and a wander when it gets nowhere, the throwaway blocks it carries for every wall. Its
// fittings (a door, and the crafting table and furnace it carries, where the blueprint keeps a chest and room) go in after,
// each by AltoClef's PlaceBlockTask. The rest is written for this mod.
package com.mineagent.engine.alto.tasks.construction;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasks.movement.TimeoutWanderTask;
import com.mineagent.engine.alto.tasksystem.ITaskRequiresGrounded;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.helpers.ItemHelper;
import com.mineagent.engine.alto.util.progresscheck.MovementProgressChecker;
import com.mineagent.engine.baritone.api.schematic.AbstractSchematic;
import com.mineagent.engine.baritone.api.utils.BlockOptionalMeta;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class BuildHutTask extends Task implements ITaskRequiresGrounded {

    /** Mindcraft's dirt_shelter, blocks[y][z][x]: a 3 by 3 room sunk one block into the ground, its door on the south. */
    static final String[][][] DIRT_SHELTER = {
            {
                    {"", "", "", "", ""},
                    {"", "dirt", "dirt", "dirt", ""},
                    {"", "dirt", "dirt", "dirt", ""},
                    {"", "dirt", "dirt", "dirt", ""},
                    {"", "", "dirt", "", ""},
                    {"", "", "dirt", "", ""}
            },
            {
                    {"dirt", "dirt", "dirt", "dirt", "dirt"},
                    {"dirt", "chest", "bed", "air", "dirt"},
                    {"dirt", "air", "bed", "air", "dirt"},
                    {"dirt", "air", "air", "air", "dirt"},
                    {"dirt", "dirt", "door", "dirt", "dirt"},
                    {"dirt", "dirt", "air", "dirt", "dirt"}
            },
            {
                    {"dirt", "dirt", "dirt", "dirt", "dirt"},
                    {"dirt", "air", "air", "air", "dirt"},
                    {"dirt", "torch", "air", "air", "dirt"},
                    {"dirt", "air", "air", "air", "dirt"},
                    {"dirt", "dirt", "door", "dirt", "dirt"},
                    {"air", "air", "air", "air", "air"}
            },
            {
                    {"air", "air", "air", "air", "air"},
                    {"dirt", "dirt", "dirt", "dirt", "dirt"},
                    {"dirt", "dirt", "dirt", "dirt", "dirt"},
                    {"dirt", "dirt", "dirt", "dirt", "dirt"},
                    {"air", "air", "air", "air", "air"},
                    {"air", "air", "air", "air", "air"}
            }
    };
    /** The blueprint's first layer is this far below the hut's place (Mindcraft's "offset"). */
    static final int OFFSET = -2;
    private static final int SIZE_X = 5, SIZE_Y = 4, SIZE_Z = 6;
    /** Where its fittings go, in the blueprint (x, layer, z): the chest's place takes its crafting table, the far corner its furnace. */
    private static final int[] TABLE_AT = {1, 1, 1};
    private static final int[] FURNACE_AT = {3, 1, 1};
    private static final int[] DOOR_AT = {2, 1, 4};
    /** The middle of the room, where it stands in its hut. */
    private static final int[] INSIDE_AT = {2, 1, 3};

    private final BlockPos place;
    private final MovementProgressChecker progressChecker = new MovementProgressChecker();
    /** SMH: the walls still to go up when it last saw them go up, and when that was (a builder places from where it stands). */
    private int lastMissing = Integer.MAX_VALUE;
    private long lastGainTick;
    private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(5f);
    private Task materialTask;
    private boolean building;

    /** A hut with its place (Mindcraft's "position": its ground layer is one below it) at {@code place}. */
    public BuildHutTask(BlockPos place) {
        this.place = place.immutable();
    }

    /** The hut's place: the block a player standing on the ground there has their feet in. */
    public BlockPos getPlace() {
        return place;
    }

    /** Where it stands inside its hut. */
    public BlockPos inside() {
        return at(INSIDE_AT);
    }

    /** The lower half of its door. */
    public BlockPos door() {
        return at(DOOR_AT);
    }

    /** Whether its door is in. */
    public boolean doorIn(AltoClef mod) {
        return mod.getWorld().getBlockState(door()).getBlock() instanceof DoorBlock;
    }

    private BlockPos at(int[] xyz) {
        return place.offset(xyz[0], xyz[1] + OFFSET, xyz[2]);
    }

    private static String cell(int x, int y, int z) {
        return DIRT_SHELTER[y][z][x];
    }

    /** Whether the hut's shell stands at {@code place}: its walls, floor and roof solid, its room and doorway clear. */
    public static boolean shellStands(AltoClef mod, BlockPos place) {
        return shellShort(mod, place) == 0 && roomClear(mod, place);
    }

    /** How many of its walls, floor and roof are not there yet. */
    private static int shellShort(AltoClef mod, BlockPos place) {
        int missing = 0;
        for (int y = 0; y < SIZE_Y; y++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    if (!cell(x, y, z).equals("dirt")) continue;
                    if (!isWall(mod.getWorld().getBlockState(place.offset(x, y + OFFSET, z)), mod, place.offset(x, y + OFFSET, z))) missing++;
                }
            }
        }
        return missing;
    }

    private static boolean roomClear(AltoClef mod, BlockPos place) {
        return roomLeft(mod, place) == 0;
    }

    /** How many blocks of its room and doorway are still to be cleared. */
    private static int roomLeft(AltoClef mod, BlockPos place) {
        int left = 0;
        for (int y = 0; y < SIZE_Y; y++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    String want = cell(x, y, z);
                    if (want.isEmpty() || want.equals("dirt")) continue;
                    BlockState now = mod.getWorld().getBlockState(place.offset(x, y + OFFSET, z));
                    if (!now.isAir() && !isFitting(now)) left++;
                }
            }
        }
        return left;
    }

    private static boolean isWall(BlockState state, AltoClef mod, BlockPos pos) {
        return !state.isAir() && state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(mod.getWorld(), pos)
                && !isFitting(state);
    }

    /** A fitting its room may hold: a door, a crafting table, a furnace, a chest, a bed, a torch. */
    private static boolean isFitting(BlockState state) {
        Block b = state.getBlock();
        return b instanceof DoorBlock || b == Blocks.CRAFTING_TABLE || b == Blocks.FURNACE || b == Blocks.CHEST
                || b instanceof net.minecraft.world.level.block.BedBlock || b == Blocks.TORCH || b == Blocks.WALL_TORCH;
    }

    private int throwaways(AltoClef mod) {
        return mod.getItemStorage().getItemCount(mod.getClientBaritoneSettings().acceptableThrowawayItems.value.toArray(new Item[0]));
    }

    @Override
    protected void onStart() {
        progressChecker.reset();
        building = false;
        lastMissing = Integer.MAX_VALUE;
        lastGainTick = AltoClef.getInstance().getWorld().getGameTime();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        // AltoClef's PlaceBlockTask: a wander when it got nowhere, then on.
        if (wanderTask.isActive() && !wanderTask.isFinished()) {
            setDebugState("Wandering (the hut got nowhere).");
            progressChecker.reset();
            return wanderTask;
        }
        int missing = shellShort(mod, place);
        if (missing > 0 || !roomClear(mod, place)) {
            // Its material task: the blocks for the walls it still lacks, and a few over.
            if (materialTask != null && materialTask.isActive() && !materialTask.isFinished() && throwaways(mod) < missing + 4) {
                setDebugState("Getting blocks for the hut's walls");
                return materialTask;
            }
            materialTask = null;
            if (throwaways(mod) < missing) {
                stopBuilder(mod);
                materialTask = PlaceBlockTask.getMaterialTask(missing + 8);
                progressChecker.reset();
                return materialTask;
            }
            // AltoClef's PlaceBlockTask wanders when it got nowhere; a hut is many blocks placed from a few places, so here
            // nowhere is half a minute with no wall gone up and no step taken (its own progress check, while it walks).
            long now = mod.getWorld().getGameTime();
            int toGo = missing + roomLeft(mod, place);
            if (toGo < lastMissing) {
                lastMissing = toGo;
                lastGainTick = now;
                progressChecker.reset();
            }
            boolean walking = mod.getClientBaritone().getPathingBehavior().isPathing();
            if (walking ? !progressChecker.check(mod) : now - lastGainTick > 600) {
                stopBuilder(mod);
                lastGainTick = now;
                lastMissing = Integer.MAX_VALUE;
                return wanderTask;
            }
            setDebugState("Letting Baritone build the hut (" + missing + " blocks to go)");
            if (!mod.getClientBaritone().getBuilderProcess().isActive()) {
                mod.getClientBaritone().getBuilderProcess().build("hut", new HutSchematic(mod), place.offset(0, OFFSET, 0));
                building = true;
            }
            return null;
        }
        stopBuilder(mod);
        // Its fittings: a door (made when it has none), then the table and furnace it carries.
        if (!(mod.getWorld().getBlockState(door()).getBlock() instanceof DoorBlock)) {
            if (!mod.getItemStorage().hasItem(ItemHelper.WOOD_DOOR)) {
                setDebugState("Making a door for the hut");
                return TaskCatalogue.getItemTask("door", 1);
            }
            setDebugState("Putting the hut's door in");
            return new PlaceBlockTask(door(), ItemHelper.itemsToBlocks(ItemHelper.WOOD_DOOR));
        }
        if (mod.getItemStorage().hasItem(Items.CRAFTING_TABLE) && !mod.getWorld().getBlockState(at(TABLE_AT)).is(Blocks.CRAFTING_TABLE)) {
            setDebugState("Putting its crafting table in the hut");
            return new PlaceBlockTask(at(TABLE_AT), Blocks.CRAFTING_TABLE);
        }
        if (mod.getItemStorage().hasItem(Items.FURNACE) && !mod.getWorld().getBlockState(at(FURNACE_AT)).is(Blocks.FURNACE)) {
            setDebugState("Putting its furnace in the hut");
            return new PlaceBlockTask(at(FURNACE_AT), Blocks.FURNACE);
        }
        setDebugState("The hut stands");
        return null;
    }

    private void stopBuilder(AltoClef mod) {
        if (building) {
            mod.getClientBaritone().getBuilderProcess().onLostControl();
            building = false;
        }
    }

    @Override
    protected void onStop(Task interruptTask) {
        stopBuilder(AltoClef.getInstance());
    }

    @Override
    public boolean isFinished() {
        AltoClef mod = AltoClef.getInstance();
        if (!shellStands(mod, place)) return false;
        if (!(mod.getWorld().getBlockState(door()).getBlock() instanceof DoorBlock)) return false;
        if (mod.getItemStorage().hasItem(Items.CRAFTING_TABLE) && !mod.getWorld().getBlockState(at(TABLE_AT)).is(Blocks.CRAFTING_TABLE)) return false;
        return !mod.getItemStorage().hasItem(Items.FURNACE) || mod.getWorld().getBlockState(at(FURNACE_AT)).is(Blocks.FURNACE);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BuildHutTask task && task.place.equals(place);
    }

    @Override
    protected String toDebugString() {
        return "Building a hut at " + place.toShortString();
    }

    /**
     * The hut for Baritone's builder (as AltoClef's PlaceStructureSchematic): a wall is any full block already there, else
     * a throwaway block it carries; the room and doorway are air, a fitting already in them kept; "" is any block.
     */
    private class HutSchematic extends AbstractSchematic {
        private final AltoClef mod;

        HutSchematic(AltoClef mod) {
            super(SIZE_X, SIZE_Y, SIZE_Z);
            this.mod = mod;
        }

        @Override
        public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> available) {
            String want = cell(x, y, z);
            if (want.isEmpty()) return current;
            if (want.equals("dirt")) {
                BlockPos pos = place.offset(x, y + OFFSET, z);
                if (isWall(current, mod, pos)) return current;
                for (BlockState possible : available) {
                    if (possible != null && mod.getClientBaritoneSettings().acceptableThrowawayItems.value.contains(possible.getBlock().asItem())) {
                        return possible;
                    }
                }
                return new BlockOptionalMeta(Blocks.DIRT).getAnyBlockState();
            }
            return isFitting(current) ? current : Blocks.AIR.defaultBlockState();
        }
    }
}
