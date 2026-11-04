package adris.altoclef.multiversion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

public class BlockTagVer {


    public static boolean isWool(Block block) {
        //#if MC >= 11802
        return BuiltInRegistries.BLOCK.getResourceKey(block).map(e -> BuiltInRegistries.BLOCK.getHolderOrThrow(e).tags().anyMatch(t -> t == BlockTags.WOOL)).orElse(false);
        //#else
        //$$ return BlockTags.WOOL.contains(block);
        //#endif
    }

}
