package adris.altoclef.tasks.construction;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.ITaskRequiresGrounded;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.EntityHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.time.TimerGame;

public class ProjectileProtectionWallTask extends Task implements ITaskRequiresGrounded {


	private final AltoClef mod;
    private final TimerGame waitForBlockPlacement = new TimerGame(2);

    private BlockPos targetPlacePos;
	
	public ProjectileProtectionWallTask(AltoClef mod) {
		this.mod = mod;
	}
	
	@Override
	protected void onStart() {
		waitForBlockPlacement.forceElapse();
	}

	@Override
	protected Task onTick() {
		if (targetPlacePos != null && !WorldHelper.isSolidBlock(targetPlacePos)) {
			Optional<adris.altoclef.util.slots.Slot> slot = StorageHelper.getSlotWithThrowawayBlock(this.mod, true);
			if(slot.isPresent()) {
				place(targetPlacePos, InteractionHand.MAIN_HAND, slot.get().getInventorySlot());
				targetPlacePos = null;
				setDebugState(null);
			}
			return null;
		}

		Optional<Entity> sentity = mod.getEntityTracker().getClosestEntity((e) -> {
        	if(e instanceof Skeleton 
        			&& EntityHelper.isAngryAtPlayer(mod, e)
        			&& 
        			(((Skeleton) e).getTicksUsingItem() > 8)
        			) return true;
        	return false;
        }, Skeleton.class);
        if(sentity.isPresent()) {
    		Vec3 playerPos = mod.getPlayer().position();
            Vec3 targetPos = sentity.get().position();
    		// Calculate the direction vector towards the target entity
            Vec3 direction = playerPos.subtract(targetPos).normalize();

            // Calculate the new position two blocks away in the direction of the entity
            double x = playerPos.x - 2 * direction.x;
            double y = playerPos.y + direction.y;
            double z = playerPos.z - 2 * direction.z;
            
            targetPlacePos = new BlockPos((int) x, (int) y+1, (int) z);
			setDebugState("Placing at " + targetPlacePos.toString());
			waitForBlockPlacement.reset();
        }
		return null;
	}

	@Override
	protected void onStop(Task interruptTask) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
    public boolean isFinished() {
        assert Minecraft.getInstance().level != null;
        
        Optional<Entity> entity = mod.getEntityTracker().getClosestEntity((e) -> {
        	if(e instanceof Skeleton 
        			&& EntityHelper.isAngryAtPlayer(mod, e)
        			&& 
        			(((Skeleton) e).getTicksUsingItem() > 3)
        			) return true;
        	return false;
        }, Skeleton.class);
        
        return targetPlacePos != null && WorldHelper.isSolidBlock(targetPlacePos) || entity.isEmpty();
    }

	@Override
	protected boolean isEqual(Task other) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	protected String toDebugString() {
		// TODO Auto-generated method stub
		return "Placing blocks to block projectiles";
	}
	
	public Direction getPlaceSide(BlockPos blockPos) {
        for (Direction side : Direction.values()) {
            BlockPos neighbor = blockPos.relative(side);
            BlockState state = mod.getWorld().getBlockState(neighbor);

            // Check if neighbour isn't empty
            if (state.isAir() || isClickable(state.getBlock())) continue;

            // Check if neighbour is a fluid
            if (!state.getFluidState().isEmpty()) continue;

            return side;
        }

        return null;
    }
	
	public boolean place(BlockPos blockPos, InteractionHand hand, int slot) {
        if (slot < 0 || slot > 8) return false;
        if (!canPlace(blockPos)) return false;

        Vec3 hitPos = Vec3.atCenterOf(blockPos);

        BlockPos neighbour;
        Direction side = getPlaceSide(blockPos);

        if (side == null) {
        	place(blockPos.below(), hand, slot);
        	return false;
        } else {
            neighbour = blockPos.relative(side);
            hitPos = hitPos.add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
        }

        BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);

        mod.getPlayer().setYRot((float) getYaw(hitPos));
        mod.getPlayer().setXRot((float) getPitch(hitPos));
		swap(slot);

        interact(bhr, hand);


        return true;
    }
    
	
	public static boolean isClickable(Block block) {
        return block instanceof CraftingTableBlock
            || block instanceof AnvilBlock
            || block instanceof ButtonBlock
            || block instanceof BasePressurePlateBlock
            || block instanceof BaseEntityBlock
            || block instanceof BedBlock
            || block instanceof FenceGateBlock
            || block instanceof DoorBlock
            || block instanceof NoteBlock
            || block instanceof TrapDoorBlock;
    }
	
	public void interact(BlockHitResult blockHitResult, InteractionHand hand) {
        boolean wasSneaking = mod.getPlayer().input.shiftKeyDown;
        mod.getPlayer().input.shiftKeyDown = false;

        InteractionResult result = mod.getController().useItemOn(mod.getPlayer(),hand, blockHitResult);

        if (result.shouldSwing()) {
            mod.getPlayer().swing(hand);
        }

        mod.getPlayer().input.shiftKeyDown = wasSneaking;
    }

	public boolean canPlace(BlockPos blockPos, boolean checkEntities) {
        if (blockPos == null) return false;

        // Check y level
        if (!Level.isInSpawnableBounds(blockPos) || !AltoClef.getInstance().getWorld().isInWorldBounds(blockPos)) return false;

        // Check if current block is replaceable
        if (!mod.getWorld().getBlockState(blockPos).canBeReplaced()) return false;

        // Check if intersects entities
        return !checkEntities || mod.getWorld().isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), blockPos, CollisionContext.empty());
    }

    public boolean canPlace(BlockPos blockPos) {
        return canPlace(blockPos, true);
    }
	
    public boolean swap(int slot) {
        if (slot == PlayerSlot.OFFHAND_SLOT.getInventorySlot()) return true;
        if (slot < 0 || slot > 8) return false;

        mod.getPlayer().getInventory().selected = slot;
        return true;
    }
    
    public double getYaw(Vec3 pos) {
        return mod.getPlayer().getYRot() + Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(pos.z() - mod.getPlayer().getZ(), pos.x() - mod.getPlayer().getX())) - 90f - mod.getPlayer().getYRot());
    }

    public double getPitch(Vec3 pos) {
        double diffX = pos.x() - mod.getPlayer().getX();
        double diffY = pos.y() - (mod.getPlayer().getY() + mod.getPlayer().getEyeHeight(mod.getPlayer().getPose()));
        double diffZ = pos.z() - mod.getPlayer().getZ();

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        return mod.getPlayer().getXRot() + Mth.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)) - mod.getPlayer().getXRot());
    }
}