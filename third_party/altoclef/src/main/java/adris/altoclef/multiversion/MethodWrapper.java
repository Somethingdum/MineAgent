package adris.altoclef.multiversion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;

public class MethodWrapper {



    public static Entity getRenderedEntity(BaseSpawner logic, Level world, BlockPos pos) {
        //#if MC>12002
        return logic.getOrCreateDisplayEntity(world, pos);
        //#elseif MC >= 11904
        //$$ return logic.getRenderedEntity(world,Random.create() ,pos);
        //#elseif MC >= 11701
        //$$ return logic.getRenderedEntity(world);
        //#else
        //$$ return logic.getRenderedEntity();
        //#endif
    }

    public static float getDamageLeft(LivingEntity armorWearer, double damage, DamageSource source, double armor, double armorToughness) {
        return getDamageLeft(armorWearer, (float)damage,source,(float)armor,(float)armorToughness);
    }

    public static float getDamageLeft(LivingEntity armorWearer, float damage, DamageSource source, float armor, float armorToughness) {
        //#if MC >= 12100
        return CombatRules.getDamageAfterAbsorb(armorWearer, damage, source, armor, armorToughness);
        //#elseif MC>=12005
        //$$ return DamageUtil.getDamageLeft(damage, source, armor, armorToughness);
        //#else
        //$$ return DamageUtil.getDamageLeft(damage,armor,armorToughness);
        //#endif
    }



}
