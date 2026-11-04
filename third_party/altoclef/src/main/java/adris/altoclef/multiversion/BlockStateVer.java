package adris.altoclef.multiversion;

import net.minecraft.world.level.block.state.BlockState;

public class BlockStateVer {


    @Pattern
    private static boolean isSolid(BlockState state) {
        //#if MC >= 12001
        return state.isSolid();
        //#else
        //$$ return state.getMaterial().isSolid();
        //#endif
    }

    @Pattern
    private static boolean isReplaceable(BlockState state) {
        //#if MC >= 11904
        return state.canBeReplaced();
        //#else
        //$$ return state.getMaterial().isReplaceable();
        //#endif
    }

    @Pattern
    private static float getHardness(BlockState state) {
        //#if MC >= 11701
        return state.getBlock().defaultDestroyTime();
        //#else
        //$$ return state.getHardness(null, null);
        //#endif
    }

}
