package adris.altoclef.multiversion.box;

import adris.altoclef.multiversion.Pattern;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BoxVer {


   @Pattern
    public AABB of(Vec3 center, double x, double y, double z) {
       //#if MC >= 11701
       return AABB.ofSize(center, x, y, z);
       //#else
       //$$ return adris.altoclef.multiversion.box.BoxHelper.of(center, x, y, z);
       //#endif
   }


}
