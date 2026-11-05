package me.sailex.altoclef.trackers.blacklisting;

import me.sailex.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class WorldLocateBlacklist extends AbstractObjectBlacklist<BlockPos> {
   protected Vec3 getPos(BlockPos item) {
      return WorldHelper.toVec3d(item);
   }
}
