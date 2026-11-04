package adris.altoclef.multiversion;

import net.minecraft.network.chat.ChatType;

public class MessageTypeVer {

    //#if MC >= 11904
    public static ChatType getMessageType(ChatType.Bound parameters) {
    //#else
    //$$ public static MessageType getMessageType(Object obj) {
    //#endif

        //#if MC >= 12005
        return parameters.chatType().value();
        //#elseif MC >= 11904
        //$$ return parameters.type();
        //#else
        //$$ throw new IllegalStateException("Cannot get message type from params since they do not exist in this version!");
        //#endif
    }
}
