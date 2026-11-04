package adris.altoclef.multiversion.entity;

import adris.altoclef.multiversion.Pattern;
import net.fabricmc.loader.impl.lib.sat4j.core.Vec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import adris.altoclef.mixins.EntityAccessor;

public class EntityVer {


    @Pattern
    public boolean isInNetherPortal(Entity entity) {
        //#if MC <= 12006
        //$$ return ((EntityAccessor)entity).isInNetherPortal();
        //#else
        return adris.altoclef.multiversion.entity.EntityHelper.isInNetherPortal(entity);
        //#endif
    }

    @Pattern
    public int getPortalCooldown(Entity entity) {
        //#if MC >= 12001
        return entity.getPortalCooldown();
        //#else
        //$$ return ((EntityAccessor) entity).getPortalCooldown();
        //#endif
    }


    @Pattern
    public BlockPos getLandingPos(Entity entity) {
        //#if MC >= 11701
        return entity.getOnPos();
        //#else
        //$$ return ((adris.altoclef.mixins.EntityAccessor) entity).invokeGetLandingPos();
        //#endif
    }

    @Pattern
    private static float getPitch(Entity player) {
        //#if MC >= 11701
        return player.getXRot();
        //#else
        //$$ return player.pitch;
        //#endif
    }

    @Pattern
    private static float getYaw(Entity player) {
        //#if MC >= 11701
        return player.getYRot();
        //#else
        //$$ return player.yaw;
        //#endif
    }

    @Pattern
    private static void setPitch(Entity player, float value) {
        //#if MC >= 11701
        player.setXRot(value);
        //#else
        //$$ player.pitch = value;
        //#endif
    }

    @Pattern
    private static void setYaw(Entity player, float value) {
        //#if MC >= 11701
        player.setYRot(value);
        //#else
        //$$ player.yaw = value;
        //#endif
    }

    @Pattern
    private static Vec3 getEyePos(Entity entity) {
        //#if MC >= 11701
        return entity.getEyePosition();
        //#else
        //$$ return adris.altoclef.multiversion.entity.EntityHelper.getEyePos(entity);
        //#endif
    }

    @Pattern
    private static ChunkPos getChunkPos(Entity entity) {
        //#if MC >= 11701
        return entity.chunkPosition();
        //#else
        //$$ return adris.altoclef.multiversion.entity.EntityHelper.getChunkPos(entity);
        //#endif
    }

    @Pattern
    private static int getBlockX(Entity entity) {
        //#if MC >= 11701
        return entity.getBlockX();
        //#else
        //$$ return adris.altoclef.multiversion.entity.EntityHelper.getBlockX(entity);
        //#endif
    }

    @Pattern
    private static int getBlockY(Entity entity) {
        //#if MC >= 11701
        return entity.getBlockY();
        //#else
        //$$ return adris.altoclef.multiversion.entity.EntityHelper.getBlockY(entity);
        //#endif
    }

    @Pattern
    private static int getBlockZ(Entity entity) {
        //#if MC >= 11701
        return entity.getBlockZ();
        //#else
        //$$ return adris.altoclef.multiversion.entity.EntityHelper.getBlockZ(entity);
        //#endif
    }

}
