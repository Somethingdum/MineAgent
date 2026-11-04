package adris.altoclef.multiversion;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

public class DamageSourceVer {


    public static DamageSource getFallDamageSource(Level world) {
        //#if MC >= 11904
        return world.damageSources().fall();
        //#else
        //$$ return DamageSource.FALL;
        //#endif
    }

}
