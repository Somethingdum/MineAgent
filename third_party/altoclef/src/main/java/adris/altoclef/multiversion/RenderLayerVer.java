package adris.altoclef.multiversion;

import net.minecraft.client.renderer.RenderType;

public class RenderLayerVer {


    public static RenderType getGuiOverlay() {
        //#if MC >= 12001
        return RenderType.guiOverlay();
        //#else
        //$$ return null;
        //#endif
    }

}
