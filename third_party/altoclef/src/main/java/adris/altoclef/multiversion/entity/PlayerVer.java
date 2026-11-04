package adris.altoclef.multiversion.entity;

import adris.altoclef.multiversion.Pattern;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class PlayerVer {


    public static void sendChatMessage(LocalPlayer player,String content) {
        //#if MC >= 11904
        player.connection.sendChat(content);
        //#else
        //$$ player.sendChatMessage(content);
        //#endif
    }

    public static void sendChatCommand(LocalPlayer player,String content) {
        //#if MC >= 11904
        player.connection.sendCommand(content);
        //#else
        //$$ player.sendChatMessage("/"+content);
        //#endif
    }

    @Pattern
    private static ItemStack getCursorStack(Player player) {
        //#if MC >= 11701
        return player.containerMenu.getCarried();
        //#else
        //$$ return player.inventory.getCursorStack();
        //#endif
    }

    @Pattern
    private static Container getInventory(Player player) {
        //#if MC >= 11701
        return player.getInventory();
        //#else
        //$$ return player.inventory;
        //#endif
    }

    public static boolean inPowderedSnow(Player player) {
        //#if MC >= 11701
        return player.isInPowderSnow;
        //#else
        //$$ return false;
        //#endif
    }



}
