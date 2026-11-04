package adris.altoclef.util.helpers;

import adris.altoclef.AltoClef;
import adris.altoclef.mixins.ClientConnectionAccessor;
import adris.altoclef.multiversion.MethodWrapper;
import adris.altoclef.multiversion.world.WorldVer;
import adris.altoclef.util.Dimension;
import baritone.api.BaritoneAPI;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.MovementHelper;
import baritone.process.MineProcess;
import baritone.utils.BlockStateInterface;
import net.minecraft.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.network.Connection;
import net.minecraft.util.math.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Super useful helper functions for getting information about the world.
 */
public interface WorldHelper {

    /**
     * Get the number of in-game ticks the game/world has been active for.
     */
    static int getTicks() {
        Connection con = Objects.requireNonNull(Minecraft.getInstance().getConnection()).getConnection();
        return ((ClientConnectionAccessor) con).getTicks();
    }

    static Vec3 toVec3d(BlockPos pos) {
        if (pos == null) return null;
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    static Vec3 toVec3d(Vec3i pos) {
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

    static Vec3i toVec3i(Vec3 pos) {
        return new Vec3i((int) pos.x(), (int) pos.y(), (int) pos.z());
    }

    static BlockPos toBlockPos(Vec3 pos) {
        return new BlockPos((int) pos.x(), (int) pos.y(), (int) pos.z());
    }

    static boolean isSourceBlock(BlockPos pos, boolean onlyAcceptStill) {
        ClientLevel world = AltoClef.getInstance().getWorld();

        BlockState s = world.getBlockState(pos);
        if (s.getBlock() instanceof LiquidBlock) {
            // Only accept still fluids.
            if (!s.getFluidState().isSource() && onlyAcceptStill) return false;
            int level = s.getFluidState().getAmount();
            // Ignore if there's liquid above, we can't tell if it's a source block or not.
            BlockState above = world.getBlockState(pos.above());
            if (above.getBlock() instanceof LiquidBlock) return false;
            return level == 8;
        }
        return false;
    }

    static double distanceXZSquared(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        return (delta.x * delta.x) + (delta.z * delta.z);
    }

    static double distanceXZ(Vec3 from, Vec3 to) {
        return Math.sqrt(distanceXZSquared(from, to));
    }

    static boolean inRangeXZ(Vec3 from, Vec3 to, double range) {
        return distanceXZSquared(from, to) < range * range;
    }

    static boolean inRangeXZ(BlockPos from, BlockPos to, double range) {
        return inRangeXZ(toVec3d(from), toVec3d(to), range);
    }

    static boolean inRangeXZ(Entity entity, Vec3 to, double range) {
        return inRangeXZ(entity.position(), to, range);
    }

    static boolean inRangeXZ(Entity entity, BlockPos to, double range) {
        return inRangeXZ(entity, toVec3d(to), range);
    }

    static boolean inRangeXZ(Entity entity, Entity to, double range) {
        return inRangeXZ(entity, to.position(), range);
    }

    static Dimension getCurrentDimension() {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) return Dimension.OVERWORLD;
        if (world.dimensionType().ultraWarm()) return Dimension.NETHER;
        if (world.dimensionType().natural()) return Dimension.OVERWORLD;
        return Dimension.END;
    }

    /**
     * WARNING: this method checks if the block at the given position is a SOLID BLOCK
     * things like ice, dirtPaths, soulSand... don't count into this
     * if you just want to check if a block is solid use `BlockState.isSolid()`
     * (which includes more variety of blocks including the mentioned ones, signs, pressure plates...)
     *
     * better method for blocks that can be walked on should be created instead
     */
    static boolean isSolidBlock(BlockPos pos) {
        ClientLevel world = AltoClef.getInstance().getWorld();

        return world.getBlockState(pos).isRedstoneConductor(world, pos);
    }

    /**
     * Get the "head" of a block with a bed, if the block is a bed.
     */
    static BlockPos getBedHead(BlockPos posWithBed) {
        ClientLevel world = AltoClef.getInstance().getWorld();

        BlockState state = world.getBlockState(posWithBed);
        if (state.getBlock() instanceof BedBlock) {
            Direction facing = state.getValue(BedBlock.FACING);
            if (world.getBlockState(posWithBed).getValue(BedBlock.PART).equals(BedPart.HEAD)) {
                return posWithBed;
            }
            return posWithBed.relative(facing);
        }
        return null;
    }

    /**
     * Get the "foot" of a block with a bed, if the block is a bed.
     */
    static BlockPos getBedFoot(BlockPos posWithBed) {
        ClientLevel world = AltoClef.getInstance().getWorld();

        BlockState state = world.getBlockState(posWithBed);
        if (state.getBlock() instanceof BedBlock) {
            Direction facing = state.getValue(BedBlock.FACING);
            if (world.getBlockState(posWithBed).getValue(BedBlock.PART).equals(BedPart.FOOT)) {
                return posWithBed;
            }
            return posWithBed.relative(facing.getOpposite());
        }
        return null;
    }

    // Get the left side of a chest, given a block pos.
    // Used to consistently identify whether a double chest is part of the same chest.
    static BlockPos getChestLeft(BlockPos posWithChest) {
        BlockState state = AltoClef.getInstance().getWorld().getBlockState(posWithChest);

        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type == ChestType.SINGLE || type == ChestType.LEFT) {
                return posWithChest;
            }
            Direction facing = state.getValue(ChestBlock.FACING);
            return posWithChest.relative(facing.getCounterClockWise());
        }
        return null;
    }

    static boolean isChestBig(BlockPos posWithChest) {
        BlockState state = AltoClef.getInstance().getWorld().getBlockState(posWithChest);
        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            return (type == ChestType.RIGHT || type == ChestType.LEFT);
        }
        return false;
    }

    static int getGroundHeight(int x, int z) {
        ClientLevel world = AltoClef.getInstance().getWorld();

        for (int y = world.getMaxBuildHeight(); y >= world.getMinBuildHeight(); --y) {
            BlockPos check = new BlockPos(x, y, z);
            if (isSolidBlock(check)) return y;
        }
        return -1;
    }

    static BlockPos getADesertTemple() {
        ClientLevel world = AltoClef.getInstance().getWorld();

        List<BlockPos> stonePressurePlates = AltoClef.getInstance().getBlockScanner().getKnownLocations(Blocks.STONE_PRESSURE_PLATE);
        if (!stonePressurePlates.isEmpty()) {
            for (BlockPos pos : stonePressurePlates) {
                if (world.getBlockState(pos).getBlock() == Blocks.STONE_PRESSURE_PLATE && // Duct tape
                        world.getBlockState(pos.below()).getBlock() == Blocks.CUT_SANDSTONE &&
                        world.getBlockState(pos.below(2)).getBlock() == Blocks.TNT) {
                    return pos;
                }
            }
        }
        return null;
    }

    static boolean isUnopenedChest(BlockPos pos) {
        return AltoClef.getInstance().getItemStorage().getContainerAtPosition(pos).isEmpty();
    }

    static int getGroundHeight(int x, int z, Block... groundBlocks) {
        ClientLevel world = AltoClef.getInstance().getWorld();

        Set<Block> possibleBlocks = new HashSet<>(Arrays.asList(groundBlocks));
        for (int y = world.getMaxBuildHeight(); y >= world.getMinBuildHeight(); --y) {
            BlockPos check = new BlockPos(x, y, z);
            if (possibleBlocks.contains(world.getBlockState(check).getBlock())) return y;

        }
        return -1;
    }

    static boolean canBreak(BlockPos pos) {
        AltoClef altoClef = AltoClef.getInstance();

        // JANK: Temporarily check if we can break WITHOUT paused interactions.
        // Not doing this creates bugs where we loop back and forth through the nether portal and stuff.
        boolean prevInteractionPaused = altoClef.getExtraBaritoneSettings().isInteractionPaused();

        altoClef.getExtraBaritoneSettings().setInteractionPaused(false);

        boolean canBreak = altoClef.getWorld().getBlockState(pos).getDestroySpeed(altoClef.getWorld(), pos) >= 0
                && !altoClef.getExtraBaritoneSettings().shouldAvoidBreaking(pos)
                && MineProcess.plausibleToBreak(new CalculationContext(altoClef.getClientBaritone()), pos)
                && canReach(pos);

        altoClef.getExtraBaritoneSettings().setInteractionPaused(prevInteractionPaused);

        return canBreak;
    }

    static boolean isInNetherPortal() {
        LocalPlayer player = AltoClef.getInstance().getPlayer();

        if (player == null)
            return false;
        return adris.altoclef.multiversion.entity.EntityHelper.isInNetherPortal(player);
    }

    static boolean dangerousToBreakIfRightAbove(BlockPos toBreak) {
        AltoClef altoClef = AltoClef.getInstance();

        // There might be mumbo jumbo next to it, we fall and we get killed by lava or something.
        if (MovementHelper.avoidBreaking(altoClef.getClientBaritone().bsi, toBreak.getX(), toBreak.getY(), toBreak.getZ(), altoClef.getWorld().getBlockState(toBreak))) {
            return true;
        }
        // Fall down
        for (int dy = 1; dy <= toBreak.getY() - altoClef.getWorld().getMinBuildHeight(); ++dy) {
            BlockPos check = toBreak.below(dy);
            BlockState s = altoClef.getWorld().getBlockState(check);
            boolean tooFarToFall = dy > altoClef.getClientBaritoneSettings().maxFallHeightNoWater.value;
            // Don't fall in lava
            if (MovementHelper.isLava(s))
                return true;
            // Always fall in water
            // TODO: If there's a 1 meter thick layer of water and then a massive drop below, the bot will think it is safe.
            if (MovementHelper.isWater(s))
                return true;
            // We hit ground, depends
            if (WorldHelper.isSolidBlock(check)) {
                return tooFarToFall;
            }
        }
        // At this point we probably fall through the void, so not safe.
        return true;
    }

    static boolean canPlace(BlockPos pos) {
        return !AltoClef.getInstance().getExtraBaritoneSettings().shouldAvoidPlacingAt(pos)
                && canReach(pos);
    }

    static boolean canReach(BlockPos pos) {
        AltoClef altoClef = AltoClef.getInstance();

        if (altoClef.getModSettings().shouldAvoidOcean()) {
            // 45 is roughly the ocean floor. We add 2 just cause why not.
            // This > 47 can clearly cause a stuck bug.
            if (altoClef.getPlayer().getY() > 47 && altoClef.getChunkTracker().isChunkLoaded(pos) && isOcean(altoClef.getWorld().getBiome(pos))) { // But if we stuck, add more oceans
                // Block is in an ocean biome. If it's below sea level...
                if (pos.getY() < 64 && getGroundHeight(pos.getX(), pos.getZ(), Blocks.WATER) > pos.getY()) {
                    return false;
                }
            }
        }
        return !altoClef.getBlockScanner().isUnreachable(pos);
    }

    //#if MC >= 11802
    static boolean isOcean(Holder<Biome> b) {
    //#else
    //$$ static boolean isOcean(Biome b) {
    //#endif
        return (WorldVer.isBiome(b,Biomes.OCEAN)
                || WorldVer.isBiome(b,Biomes.COLD_OCEAN)
                || WorldVer.isBiome(b,Biomes.DEEP_COLD_OCEAN)
                || WorldVer.isBiome(b,Biomes.DEEP_OCEAN)
                || WorldVer.isBiome(b,Biomes.DEEP_FROZEN_OCEAN)
                || WorldVer.isBiome(b,Biomes.DEEP_LUKEWARM_OCEAN)
                || WorldVer.isBiome(b,Biomes.LUKEWARM_OCEAN)
                || WorldVer.isBiome(b,Biomes.WARM_OCEAN)
                || WorldVer.isBiome(b,Biomes.FROZEN_OCEAN));
    }

    static boolean isAir(BlockPos pos) {
        return AltoClef.getInstance().getBlockScanner().isBlockAtPosition(pos, Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR);
        //return state.isAir() || isAir(state.getBlock());
    }

    static boolean isAir(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }

    static boolean isInteractableBlock(BlockPos pos) {
        Block block = AltoClef.getInstance().getWorld().getBlockState(pos).getBlock();
        return (block instanceof ChestBlock
                || block instanceof EnderChestBlock
                || block instanceof CraftingTableBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof EnchantingTableBlock
                || block instanceof RedStoneOreBlock
                || block instanceof BarrelBlock
        );
    }

    static boolean isInsidePlayer(BlockPos pos) {
        return pos.closerToCenterThan(AltoClef.getInstance().getPlayer().position(), 2);
    }

    static Iterable<BlockPos> getBlocksTouchingPlayer() {
        return getBlocksTouchingBox(AltoClef.getInstance().getPlayer().getBoundingBox());
    }

    static Iterable<BlockPos> getBlocksTouchingBox(AABB box) {
        BlockPos min = new BlockPos((int) box.minX, (int) box.minY, (int) box.minZ);
        BlockPos max = new BlockPos((int) box.maxX, (int) box.maxY, (int) box.maxZ);
        return scanRegion(min, max);
    }

    static Iterable<BlockPos> scanRegion(BlockPos start, BlockPos end) {
        return () -> new Iterator<>() {
            int x = start.getX(), y = start.getY(), z = start.getZ();

            @Override
            public boolean hasNext() {
                return y <= end.getY() && z <= end.getZ() && x <= end.getX();
            }

            @Override
            public BlockPos next() {
                BlockPos result = new BlockPos(x, y, z);
                ++x;
                if (x > end.getX()) {
                    x = start.getX();
                    ++z;
                    if (z > end.getZ()) {
                        z = start.getZ();
                        ++y;
                    }
                }
                return result;
            }
        };
    }

    static boolean fallingBlockSafeToBreak(BlockPos pos) {
        BlockStateInterface bsi = new BlockStateInterface(BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext());
        Level w = Minecraft.getInstance().level;
        assert w != null;
        while (isFallingBlock(pos)) {
            if (MovementHelper.avoidBreaking(bsi, pos.getX(), pos.getY(), pos.getZ(), w.getBlockState(pos)))
                return false;
            pos = pos.above();
        }
        return true;
    }

    static boolean isFallingBlock(BlockPos pos) {
        Level w = Minecraft.getInstance().level;
        assert w != null;
        return w.getBlockState(pos).getBlock() instanceof FallingBlock;
    }

    static Entity getSpawnerEntity(BlockPos pos) {
        ClientLevel world = AltoClef.getInstance().getWorld();

        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SpawnerBlock) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SpawnerBlockEntity blockEntity) {
                return MethodWrapper.getRenderedEntity(blockEntity.getSpawner(), world, pos);
            }
        }
        return null;
    }

    static Vec3 getOverworldPosition(Vec3 pos) {
        if (getCurrentDimension() == Dimension.NETHER) {
            pos = pos.multiply(8.0, 1, 8.0);
        }
        return pos;
    }

    static BlockPos getOverworldPosition(BlockPos pos) {
        if (getCurrentDimension() == Dimension.NETHER) {
            pos = new BlockPos(pos.getX() * 8, pos.getY(), pos.getZ() * 8);
        }
        return pos;
    }

    static boolean isChest(BlockPos block) {
        Block b = AltoClef.getInstance().getWorld().getBlockState(block).getBlock();
        return isChest(b);
    }

    static boolean isChest(Block b) {
        return b instanceof ChestBlock || b instanceof EnderChestBlock;
    }

    static boolean isBlock(BlockPos pos, Block block) {
        return AltoClef.getInstance().getWorld().getBlockState(pos).getBlock() == block;
    }

    static boolean canSleep() {
        ClientLevel world = Minecraft.getInstance().level;
        if (world != null) {
            // You can sleep during thunderstorms
            if (world.isThundering() && world.isRaining())
                return true;

            int time = getTimeOfDay();
            // https://minecraft.fandom.com/wiki/Daylight_cycle
            return 12542 <= time && time <= 23992;
        }

        return false;
    }

    static int getTimeOfDay() {
        ClientLevel world = Minecraft.getInstance().level;
        if (world != null) {
            // You can sleep during thunderstorms
            return (int) (world.getDayTime() % 24000);
        }
        return 0;
    }

    static boolean isVulnerable() {
        LocalPlayer player = AltoClef.getInstance().getPlayer();

        int armor = player.getArmorValue();
        float health = player.getHealth();

        if (armor <= 15 && health < 3) return true;
        if (armor < 10 && health < 10) return true;

        return armor < 5 && health < 18;
    }

    static boolean isSurroundedByHostiles() {
        List<LivingEntity> hostiles = AltoClef.getInstance().getEntityTracker().getHostiles();
        return isSurrounded(hostiles);
    }

    // Function to check if the player is surrounded on two or more sides
    static boolean isSurrounded(List<LivingEntity> entities) {
        LocalPlayer player = AltoClef.getInstance().getPlayer();

        BlockPos playerPos = player.blockPosition();

        // Minimum number of sides to consider the origin surrounded
        final int MIN_SIDES_TO_SURROUND = 2;

        // Count the number of unique sides based on angles
        List<Direction> uniqueSides =  new ArrayList<Direction>();

        // Iterate through each point and calculate the angle with the origin
        for (Entity entity : entities) {
            if(!entity.closerThan(player, 8)) continue;
            BlockPos entityPos = entity.blockPosition();
            double angle = calculateAngle(playerPos, entityPos);

            // Check if the angle is unique
            boolean isUnique = !uniqueSides.contains(getHorizontalDirectionFromYaw(angle));

            // If the angle is unique, increment the uniqueSides count
            if (isUnique) {
                uniqueSides.add(getHorizontalDirectionFromYaw(angle));
            }
        }

        // Check if the origin is surrounded on two or more sides
        return uniqueSides.size() >= MIN_SIDES_TO_SURROUND;
    }

    private static double calculateAngle(BlockPos origin, BlockPos target) {
        double translatedX = target.getX() - origin.getX();
        double translatedZ = target.getZ() - origin.getZ();
        double angleRad = Math.atan2(translatedZ, translatedX);
        double angleDeg = Math.toDegrees(angleRad);
        angleDeg -= 90;
        if (angleDeg < 0) {
            angleDeg += 360;
        }
        return angleDeg;
    }

    private static Direction getHorizontalDirectionFromYaw(double yaw) {
        yaw %= 360.0F;
        if (yaw < 0) {
            yaw += 360.0F;
        }

        if ((yaw >= 45 && yaw < 135) || (yaw >= -315 && yaw < -225)) {
            return Direction.WEST;
        } else if ((yaw >= 135 && yaw < 225) || (yaw >= -225 && yaw < -135)) {
            return Direction.NORTH;
        } else if ((yaw >= 225 && yaw < 315) || (yaw >= -135 && yaw < -45)) {
            return Direction.EAST;
        } else {
            return Direction.SOUTH;
        }
    }


}
