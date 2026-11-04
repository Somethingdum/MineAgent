package adris.altoclef.multiversion.world;

import adris.altoclef.multiversion.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class WorldVer {



    public static boolean isBiomeAtPos(Level world, ResourceKey<Biome> biome, BlockPos pos) {
        //#if MC >= 11802
        Holder<Biome> b = world.getBiome(pos);
        return b.is(biome);
        //#else
        //$$ Biome b = world.getBiome(pos);
        //$$ return world.getRegistryManager().get(Registry.BIOME_KEY).get(biome) == b;
        //#endif
    }


    //#if MC >= 11802
    public static boolean isBiome(Holder<Biome> biome1, ResourceKey<Biome> biome2) {
        return biome1.is(biome2);
    }
    //#else
    //$$ public static boolean isBiome(Biome biome1, RegistryKey<Biome> biome2) {
    //$$     World world = MinecraftClient.getInstance().world;
    //$$     return world.getRegistryManager().get(Registry.BIOME_KEY).get(biome2) == biome1;
    //$$ }
    //#endif


    @Pattern
    public static int getBottomY(Level world) {
        //#if MC >= 11701
        return world.getMinBuildHeight();
        //#else
        //$$ return adris.altoclef.multiversion.world.WorldHelper.getBottomY(world);
        //#endif
    }

    @Pattern
    public static int getTopY(Level world) {
        //#if MC >= 11701
        return world.getMaxBuildHeight();
        //#else
        //$$ return adris.altoclef.multiversion.world.WorldHelper.getTopY(world);
        //#endif
    }

    @Pattern
    private static boolean isOutOfHeightLimit(Level world,BlockPos pos) {
        //#if MC >= 11701
        return world.isOutsideBuildHeight(pos);
        //#else
        //$$ return adris.altoclef.multiversion.world.WorldHelper.isOutOfHeightLimit(world,pos);
        //#endif
    }

}
