package adris.altoclef.multiversion;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class TextVer {


    @Pattern
    public static MutableComponent empty() {
        //#if MC >= 11900
        return Component.empty();
        //#else
        //$$ return new net.minecraft.text.LiteralText("");
        //#endif
    }

    @Pattern
    public static MutableComponent literal(String str) {
        //#if MC >= 11900
        return Component.literal(str);
        //#else
        //$$ return new net.minecraft.text.LiteralText(str);
        //#endif
    }

}
