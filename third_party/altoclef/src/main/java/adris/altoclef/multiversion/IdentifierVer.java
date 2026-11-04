package adris.altoclef.multiversion;

import net.minecraft.resources.ResourceLocation;

public class IdentifierVer {


    @Pattern
    private static ResourceLocation newCreation(String str) {
        //#if MC >= 12100
        return ResourceLocation.parse(str);
        //#else
        //$$ return new Identifier(str);
        //#endif
    }


}
